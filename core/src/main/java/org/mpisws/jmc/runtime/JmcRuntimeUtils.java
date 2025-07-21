package org.mpisws.jmc.runtime;

import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;
import org.mpisws.jmc.api.util.concurrent.JmcThread;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for JMC runtime operations.
 *
 * <p>This class provides methods to create and manage JMC runtime events, synchronize method
 * execution, and handle thread join operations. It is primarily used for bytecode instrumentation
 * and is not intended for direct use within the codebase.
 */
public class JmcRuntimeUtils {

    private static final JmcSyncLocksStore syncMethodLocksStore = new JmcSyncLocksStore();

    /**
     * Creates a read event for the specified instance, owner, name, and descriptor.
     *
     * <p>This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @param instance the instance on which the field is accessed
     */
    public static void readEvent(String owner, String name, String descriptor, Object instance) {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", null);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    /**
     * Creates a read event for the specified instance, owner, name, and descriptor without
     * yielding.
     *
     * <p>This method updates the JMC runtime event without yielding control to the scheduler.
     *
     * @param instance the instance on which the field is accessed
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     */
    public static void readEventWithoutYield(
            Object instance, String owner, String name, String descriptor) {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.READ_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        var2.put("instance", instance);
        JmcRuntime.updateEvent(builder.params(var2).build());
    }

    /**
     * Creates a write event for the specified value, owner, name, descriptor, and instance without
     * yielding.
     *
     * @param value the new value being written
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @param instance the instance on which the field is accessed
     */
    public static void writeEventWithoutYield(
            Object instance, Object value, String owner, String name, String descriptor) {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", value);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        var2.put("instance", instance);
        JmcRuntime.updateEvent(builder.params(var2).build());
    }

    /**
     * Creates a write event for the specified value, owner, name, descriptor, and instance.
     *
     * <p>This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param value the new value being written
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @param instance the instance on which the field is accessed
     */
    public static void writeEvent(
            Object value, String owner, String name, String descriptor, Object instance) {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.WRITE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("newValue", value);
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    /**
     * Creates a lock acquire event for the specified owner, name, value, descriptor, and instance.
     *
     * <p>This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param owner the owner of the lock
     * @param name the name of the lock
     * @param value the value of the lock
     * @param descriptor the descriptor of the lock
     * @param instance the instance on which the lock is acquired
     */
    public static void lockAcquireEvent(
            String owner, String name, Object value, String descriptor, Object instance) {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("value", value);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    /**
     * Creates a lock acquired event for the specified instance, owner, name, value, descriptor, and
     * new value without yielding.
     *
     * <p>This method updates the JMC runtime event without yielding control to the scheduler.
     *
     * @param instance the instance on which the lock is acquired
     * @param owner the owner of the lock
     * @param name the name of the lock
     * @param value the value of the lock
     * @param descriptor the descriptor of the lock
     * @param newValue the new value after acquiring the lock
     */
    public static void lockAcquiredEventWithoutYield(
            Object instance,
            String owner,
            String name,
            Object value,
            String descriptor,
            Object newValue) {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("value", value);
        var2.put("newValue", newValue);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEvent(builder.params(var2).param("instance", instance).build());
    }

    /**
     * Creates a lock release event for the specified instance, owner, name, value, descriptor, and
     * new value.
     *
     * <p>This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param instance the instance on which the lock is released
     * @param owner the owner of the lock
     * @param name the name of the lock
     * @param value the value of the lock
     * @param descriptor the descriptor of the lock
     * @param newValue the new value after releasing the lock
     */
    public static void lockReleaseEvent(
            Object instance,
            String owner,
            String name,
            Object value,
            String descriptor,
            Object newValue) {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT).taskId(JmcRuntime.currentTask());

        HashMap<String, Object> var2 = new HashMap<>();
        var2.put("owner", owner);
        var2.put("name", name);
        var2.put("value", value);
        var2.put("newValue", newValue);
        var2.put("descriptor", descriptor);
        JmcRuntime.updateEventAndYield(builder.params(var2).param("instance", instance).build());
    }

    /**
     * Joins the specified thread, waiting indefinitely for it to finish.
     *
     * <p>This method updates the JMC runtime event and yields control to the scheduler.
     *
     * <p>Join calls used by the instrumentation to replace existing join calls. Why do these exist?
     * While bytecode instrumentation allows us to change base class, we cannot control the order in
     * which the classes are loaded. So blindly replacing calls to join join1 doesn't work and hence
     * we need to do it at runtime. These calls are added instead of thread.join calls at runtime.
     *
     * @param t the thread to join
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public static void join(Thread t) throws InterruptedException {
        join(t, 0L);
    }

    /**
     * Joins the specified thread, waiting for it to finish for a specified time.
     *
     * <p>This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param t the thread to join
     * @param millis the maximum time to wait in milliseconds
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public static void join(Thread t, long millis) throws InterruptedException {
        JmcThread jmcThread = (JmcThread) t;
        jmcThread.join1(millis);
    }

    /**
     * Joins the specified thread, waiting for it to finish for a specified time and nanoseconds.
     *
     * <p>This method updates the JMC runtime event and yields control to the scheduler.
     *
     * @param t the thread to join
     * @param millis the maximum time to wait in milliseconds
     * @param nanos additional nanoseconds to wait
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public static void join(Thread t, long millis, int nanos) throws InterruptedException {
        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++;
        }
        JmcThread jmcThread = (JmcThread) t;
        jmcThread.join1(millis);
    }

    /**
     * Checks if the given object is an instance of {@link JmcThread} and should be instrumented for
     * thread calls.
     *
     * @param t the object to check
     * @return true if the object is an instance of {@link JmcThread}, false otherwise
     */
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

    /**
     * Locks the corresponding lock of the given instance.
     *
     * <p>This method acquires a lock on the instance's hash code.
     *
     * @param instance the instance to lock
     */
    public static void syncMethodLock(Object instance) {
        syncMethodLocksStore.getLock(instance.hashCode()).lock();
    }

    /**
     * Unlocks the corresponding lock of the given instance.
     *
     * <p>This method releases a lock on the instance's hash code.
     *
     * @param instance the instance to unlock
     */
    public static void syncMethodUnLock(Object instance) {
        syncMethodLocksStore.getLock(instance.hashCode()).unlock();
    }

    /**
     * Locks the corresponding lock of the given class's static synchronized method.
     *
     * <p>This method acquires a lock on the class name's hash code.
     *
     * @param className the class name to lock
     */
    public static void syncMethodLock(String className) {
        syncMethodLocksStore.getLock(className.hashCode()).lock();
    }

    /**
     * Unlocks the corresponding lock of the given class's static synchronized method.
     *
     * <p>This method releases a lock on the class name's hash code.
     *
     * @param className the class name to unlock
     */
    public static void syncMethodUnLock(String className) {
        syncMethodLocksStore.getLock(className.hashCode()).unlock();
    }

    /**
     * Registers a synchronization lock for the given instance.
     *
     * <p>This method registers a lock based on the instance's hash code.
     *
     * @param instance the instance to register a lock for
     */
    public static void registerSyncLock(Object instance) {
        syncMethodLocksStore.registerLock(instance.hashCode());
    }

    /**
     * Registers a synchronization lock for the given class name.
     *
     * <p>This method registers a lock based on the class name's hash code.
     *
     * @param className the class name to register a lock for
     */
    public static void registerSyncLock(String className) {
        syncMethodLocksStore.registerLock(className.hashCode());
    }

    /**
     * Locks the block for the given instance.
     *
     * <p>This method acquires a lock on the instance's hash code for synchronized blocks.
     *
     * @param instance the instance to lock
     */
    public static void syncBlockLock(Object instance) {
        syncMethodLocksStore.getWithRegister(instance.hashCode()).lock();
    }

    /**
     * Unlocks the block for the given instance.
     *
     * <p>This method releases a lock on the instance's hash code for synchronized blocks.
     *
     * @param instance the instance to unlock
     */
    public static void syncBlockUnLock(Object instance) {
        syncMethodLocksStore.getWithRegister(instance.hashCode()).unlock();
    }

    /**
     * Clears all synchronization locks.
     *
     * <p>This method clears the internal store of synchronization locks.
     */
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
         * Returns a JmcReentrantLock for the given lockObject. If it does not exist, creates a new
         * one and registers it.
         *
         * <p>Note: the lock created does not call the initial write.
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
