package org.mpisws.jmc.runtime;

import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;
import org.mpisws.jmc.api.util.concurrent.JmcThread;

import java.util.HashMap;
import java.util.Map;

// Wrapper methods to create events and make JmcRuntime calls at Runtime
// These methods are invoked mainly through bytecode instrumentation and
// not meant to be used within the codebase.
public class JmcRuntimeUtils {

    private static final JmcSyncLocksStore syncMethodLocksStore = new JmcSyncLocksStore();

    public static void readEvent(String owner, String name, String descriptor, Object instance) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", null);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    public static void readEventWithoutYield(
            Object instance, String owner, String name, String descriptor) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        var2.put("instance", instance);
        JmcRuntime.updateEvent(builder.params(var2).build());
    }

    public static void writeEventWithoutYield(
            Object instance, Object value, String owner, String name, String descriptor) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", value);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        var2.put("instance", instance);
        JmcRuntime.updateEvent(builder.params(var2).build());
    }

    public static void writeEvent(
            Object value, String owner, String name, String descriptor, Object instance) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", value);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    public static void lockAcquireEvent(
            String owner, String name, Object value, String descriptor, Object instance) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.LOCK_ACQUIRE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("value", value);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    public static void lockAcquiredEventWithoutYield(
            Object instance, String owner, String name, Object value, String descriptor, Object newValue) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.LOCK_ACQUIRED_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("value", value);
        var2.put("newValue", newValue);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEvent(builder.params(var2).param("instance", instance).build());
    }

    public static void lockReleaseEvent(
            Object instance, String owner, String name, Object value, String descriptor, Object newValue) {
        RuntimeEvent.Builder builder = new RuntimeEvent.Builder();
        builder.type(RuntimeEvent.Type.LOCK_RELEASE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("value", value);
        var2.put("newValue", newValue);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());

    }

    // Join calls used by the instrumentation to replace existing join calls.
    // Why do these exist?
    // While bytecode instrumentation allows us to change base class, we cannot
    // control the order in which the classes are loaded. So blindly replacing calls to join
    // join1 doesn't work and hence we need to do it at runtime.
    // These calls are added instead of thread.join calls at runtime.
    public static void join(Thread t) throws InterruptedException {
        join(t, 0L);
    }

    public static void join(Thread t, long millis) throws InterruptedException {
        JmcThread jmcThread = (JmcThread) t;
        jmcThread.join1(millis);
    }

    public static void join(Thread t, long millis, int nanos) throws InterruptedException {
        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++;
        }
        JmcThread jmcThread = (JmcThread) t;
        jmcThread.join1(millis);
    }

    public static boolean shouldInstrumentThreadCall(Object t) {
        return JmcThread.class.isAssignableFrom(t.getClass());
    }

    // Synchronized method and blocks calls are replaced with calls to
    // These methods which maintains state in a static global instance
    // of `JmcSyncLockStore`.

    // For synchronized methods,
    // 1. In the constructor of the class or the static initializer
    //      `registerSyncLock` is called
    // 2. Then each method start is replaced with a
    //      `try {lock() ... } finally {unlock()}
    // 3. The lock is done using `syncMethodLock`
    // 4. The unlock is done using `synchMethodUnlock`
    // 5. The difference between a static sync method and an
    //      instance sync method is in the parameters passed.
    //      The object instance for the former and the classname
    //      as string for the latter

    // For synchronized blocks
    // 1. The block is seen in the bytecode as a try catch with
    //      `MONITORENTER` and `MONITOREXIT`
    // 2. We just replace the enter and exit with lock() and unlock()


    public static void syncMethodLock(Object instance) {
        syncMethodLocksStore.getLock(instance.hashCode()).lock();
    }

    public static void syncMethodUnLock(Object instance) {
        syncMethodLocksStore.getLock(instance.hashCode()).unlock();
    }

    public static void syncMethodLock(String className) {
        syncMethodLocksStore.getLock(className.hashCode()).lock();
    }

    public static void syncMethodUnLock(String className) {
        syncMethodLocksStore.getLock(className.hashCode()).unlock();
    }

    public static void registerSyncLock(Object instance) {
        syncMethodLocksStore.registerLock(instance.hashCode());
    }

    public static void registerSyncLock(String className) {
        syncMethodLocksStore.registerLock(className.hashCode());
    }

    public static void syncBlockLock(Object instance) {
        syncMethodLocksStore.getWithRegister(instance.hashCode()).lock();
    }

    public static void syncBlockUnLock(Object instance) {
        syncMethodLocksStore.getWithRegister(instance.hashCode()).unlock();
    }

    public static void clearSyncLocks() {
        syncMethodLocksStore.clear();
    }

    private static class JmcSyncLocksStore {
        private final Map<Integer, JmcReentrantLock> lockMap;

        public JmcSyncLocksStore() {
            lockMap = new HashMap<>();
        }

        public void clear() {
            lockMap.clear();
        }

        /**
         * Returns a JmcReentrantLock for the given lockObject.
         * If it does not exist, creates a new one and registers it.
         *
         * <p>Note: the lock created does not call the initial write.</p>
         *
         * @param lockObject the object to lock on
         * @return the JmcReentrantLock for the given lockObject
         */
        public JmcReentrantLock getWithRegister(Object lockObject) {
            if (!lockMap.containsKey(lockObject.hashCode())) {
                lockMap.put(lockObject.hashCode(), new JmcReentrantLock(lockObject));
            }
            return lockMap.get(lockObject.hashCode());
        }

        public JmcReentrantLock getLock(Integer hashCode) {
            return lockMap.get(hashCode);
        }

        public void registerLock(int hashcode) {
            lockMap.put(hashcode, new JmcReentrantLock());
        }
    }
}
