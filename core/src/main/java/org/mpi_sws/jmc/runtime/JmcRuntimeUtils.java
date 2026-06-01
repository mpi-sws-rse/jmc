package org.mpi_sws.jmc.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.util.concurrent.JmcReentrantLock;
import org.mpi_sws.jmc.api.util.concurrent.JmcThread;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static org.mpi_sws.jmc.api.JmcObject.handleHashCode;


/**
 * Utility class for JMC runtime operations.
 *
 * <p>This class provides methods to create and manage JMC runtime events, synchronize method
 * execution, and handle thread join operations. It is primarily used for bytecode instrumentation
 * and is not intended for direct use within the codebase.
 */
public class JmcRuntimeUtils {
    /** Logger used to trace utility operations (static-init invocation, executor registration). */
    private static final Logger LOGGER = LogManager.getLogger(JmcRuntimeUtils.class);

    /**
     * Store of {@link JmcReentrantLock}s backing instrumented {@code synchronized} methods and
     * blocks, keyed by the hash code of the locked instance or class name.
     */
    private static final JmcSyncLocksStore syncMethodLocksStore = new JmcSyncLocksStore();

    // TODO: check if we need to change the type of the list here
    /** Instrumented classes with a non-empty static initializer, in registration order. */
    private static final List<Class<?>> staticInitializedClassesList = new ArrayList<>();
    /** Names of the registered static-initialized classes, used to deduplicate registrations. */
    private static final Set<String> staticInitializedClasses = new HashSet<>();

    /** Private constructor to prevent instantiation of this utility class. */
    private JmcRuntimeUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Reports a symbolic boolean event and returns the concrete boolean decided by the strategy.
     *
     * <p>Emits a {@link JmcRuntimeEvent.Type#SYMBOLIC_EVENT} carrying the given boolean formula and
     * yields; the strategy resumes the task with a {@link PrimitiveValue}. Throws a {@link
     * RuntimeException} if the result is not a {@link PrimitiveValue}, and a {@link
     * ClassCastException} (from {@link PrimitiveValue#asBoolean()}) if it is not a boolean.
     *
     * @param formula the symbolic boolean formula to evaluate
     * @return the boolean value chosen by the strategy for this formula
     */
    public static boolean SymEvent(JmcBooleanFormula formula) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.SYMBOLIC_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("booleanFormula", formula)
                        .build();
        Object result = JmcRuntime.updateEventAndYield(event);
        // If result is not a PrimitiveValue, throw an exception
        if (!(result instanceof PrimitiveValue)) {
            throw new RuntimeException("Expected a PrimitiveValue result from SYM_EVENT, but got: " + result);
        }
        PrimitiveValue pv = (PrimitiveValue) result;
        // If the PrimitiveValue is not a boolean, an exception will be thrown when calling asBoolean(),
        // which is the expected behavior
        return pv.asBoolean();
    }

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
        syncMethodLocksStore.getLock(handleHashCode(instance)).lock();
    }

    /**
     * Unlocks the corresponding lock of the given instance.
     *
     * <p>This method releases a lock on the instance's hash code.
     *
     * @param instance the instance to unlock
     */
    public static void syncMethodUnLock(Object instance) {
        syncMethodLocksStore.getLock(handleHashCode(instance)).unlock();
    }

    /**
     * Locks the corresponding lock of the given class's static synchronized method.
     *
     * <p>This method acquires a lock on the class name's hash code.
     *
     * @param className the class name to lock
     */
    public static void syncMethodLock(String className) {
        syncMethodLocksStore.getLock(handleHashCode(className)).lock();
    }

    /**
     * Unlocks the corresponding lock of the given class's static synchronized method.
     *
     * <p>This method releases a lock on the class name's hash code.
     *
     * @param className the class name to unlock
     */
    public static void syncMethodUnLock(String className) {
        syncMethodLocksStore.getLock(handleHashCode(className)).unlock();
    }

    /**
     * Registers a synchronization lock for the given instance.
     *
     * <p>This method registers a lock based on the instance's hash code.
     *
     * @param instance the instance to register a lock for
     */
    public static void registerSyncLock(Object instance) {
        syncMethodLocksStore.registerLock(handleHashCode(instance));
    }

    /**
     * Registers a synchronization lock for the given class name.
     *
     * <p>This method registers a lock based on the class name's hash code.
     *
     * @param className the class name to register a lock for
     */
    public static void registerSyncLock(String className) {
        syncMethodLocksStore.registerLock(handleHashCode(className));
    }

    /**
     * Locks the block for the given instance.
     *
     * <p>This method acquires a lock on the instance's hash code for synchronized blocks.
     *
     * @param instance the instance to lock
     */
    public static void syncBlockLock(Object instance) {
        syncMethodLocksStore.getWithRegister(handleHashCode(instance)).lock();
    }

    /**
     * Unlocks the block for the given instance.
     *
     * <p>This method releases a lock on the instance's hash code for synchronized blocks.
     *
     * @param instance the instance to unlock
     */
    public static void syncBlockUnLock(Object instance) {
        syncMethodLocksStore.getWithRegister(handleHashCode(instance)).unlock();
    }

    /**
     * Retrieves the synchronization lock for the given instance.
     *
     * <p>This method returns the lock associated with the instance's hash code, null if none
     * exists.
     *
     * @param instance the instance to get the lock for
     * @return the JmcReentrantLock associated with the instance
     */
    public static JmcReentrantLock getSyncLock(Object instance) {
        return syncMethodLocksStore.getLock(handleHashCode(instance));
    }

    /**
     * Clears all synchronization locks.
     *
     * <p>This method clears the internal store of synchronization locks.
     */
    public static void clearSyncLocks() {
        syncMethodLocksStore.clear();
    }

    /**
     * Maps instance/class-name hash codes to the {@link JmcReentrantLock}s that back instrumented
     * {@code synchronized} methods and blocks.
     */
    private static class JmcSyncLocksStore {
        /** The hash-code-to-lock map. */
        private final Map<Integer, JmcReentrantLock> lockMap;

        /** Constructs an empty lock store. */
        public JmcSyncLocksStore() {
            lockMap = new HashMap<>();
        }

        /** Removes all registered locks. */
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
            if (!lockMap.containsKey(handleHashCode(lockObject))) {
                lockMap.put(handleHashCode(lockObject), new JmcReentrantLock(lockObject));
            }
            return lockMap.get(handleHashCode(lockObject));
        }

        /**
         * Returns the JmcReentrantLock for the given hashCode.
         *
         * @param hashCode the hash code of the lock object
         * @return the JmcReentrantLock for the given hashCode, or null if not found
         */
        public JmcReentrantLock getLock(Integer hashCode) {
            return lockMap.get(hashCode);
        }

        /**
         * Registers a new JmcReentrantLock for the given hashCode.
         *
         * @param hashcode the hash code of the lock object
         */
        public void registerLock(int hashcode) {
            lockMap.put(hashcode, new JmcReentrantLock());
        }
    }

    /**
     * Registers an instrumented class that has a static initializer.
     *
     * <p>Registration is idempotent (deduplicated by class name). Registered classes have their
     * synthetic static initializer re-invoked at the start of subsequent iterations by {@link
     * #invokeStaticInitializedClasses(int)}.
     *
     * @param clazz the instrumented class to register
     */
    public static void registerStaticInitializedClass(Class<?> clazz) {
        if (!staticInitializedClasses.contains(clazz.getName())) {
            LOGGER.debug("Static classes registered are : {}", clazz.getName());
            staticInitializedClasses.add(clazz.getName());
            staticInitializedClassesList.add(clazz);

        }
    }

    /**
     * Rewrites a compiled-class URL to point at the corresponding instrumented class location.
     *
     * <p>Maps {@code build/classes/java/{main,test}/} paths to {@code build/generated/instrumented/}.
     * On any failure the original URL is returned. Used only by the (currently disabled)
     * class-reloading path.
     *
     * @param url the original class URL
     * @return the rewritten URL, or the original URL if rewriting fails
     */
    private static URL renameClassURL(URL url) {
        String urlString = url.toString();
        urlString = urlString.replace("build/classes/java/main/", "build/generated/instrumented/");
        urlString = urlString.replace("build/classes/java/test/", "build/generated/instrumented/");
        try {
            return new URL(urlString);
        } catch (Exception e) {
            LOGGER.error("Error renaming class URL: {}", urlString, e);
            return url; // Fallback to original URL in case of error
        }
    }

    // Relic from a different method to deal with static initialized classes
    // Would reload the classes and trigger static initializers.
    //    private static void reloadStaticInitializedClasses() {
    //        if (staticInitializedClasses.isEmpty()) {
    //            LOGGER.info("No static initialized classes to reload.");
    //            return;
    //        }
    //        URL[] urls = new URL[staticInitializedClassesList.size()];
    //        for (Class<?> clazz : staticInitializedClassesList) {
    //            URL url = clazz.getResource(clazz.getSimpleName() + ".class");
    //            urls[staticInitializedClassesList.indexOf(clazz)] = renameClassURL(url);
    //        }
    //
    //        try (ReloadingClassLoader classLoader = new ReloadingClassLoader(urls)) {
    //            // This will load the classes and trigger static initializers
    //            for (Class<?> clazz : staticInitializedClassesList) {
    //                try {
    //                    classLoader.reloadClass(clazz.getCanonicalName());
    //                } catch (ClassNotFoundException e) {
    //                    LOGGER.error("Could not reload class: {}", clazz.getCanonicalName(), e);
    //                }
    //            }
    //        } catch (Exception e) {
    //            LOGGER.error("Error initializing the custom class loader", e);
    //        }
    //    }

    /**
     * Invokes the synthetic {@code $staticInitExplicit} method on every registered static-initialized
     * class.
     *
     * <p>Iterates over a snapshot of the registered classes and reflectively calls the generated
     * static-init method on each, logging (but not propagating) reflective invocation failures.
     * Called by {@link #invokeStaticInitializedClasses(int)}.
     */
    private static void invokeInstrumentedStaticMethod() {
        if (staticInitializedClasses.isEmpty()) {
            LOGGER.debug("No static initialized classes to invoke.");
            return;
        }
        List<Class<?>> snapshot = new ArrayList<>(staticInitializedClassesList);

        // Determine which method to call based on iteration
        String methodName = "$staticInitExplicit";

        for (Class<?> clazz : snapshot) {
            try {
                Method m = clazz.getDeclaredMethod(methodName);
                m.setAccessible(true);
                m.invoke(null);
                LOGGER.debug("Invoked {} in class: {}", methodName, clazz.getName());
            } catch (InvocationTargetException ite) {
                ite.getCause().printStackTrace();
                LOGGER.error("Error invoking {} in {}", methodName, clazz.getName(), ite.getCause());
            } catch (IllegalAccessException e) {
                LOGGER.error("Error invoking {} in {}", methodName, clazz.getName(), e.getCause());
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }



    /**
     * Invokes the static initializers of all registered instrumented classes.
     *
     * <p>The instrumentation introduces a synthetic {@code $staticInitExplicit} method for each class
     * that has a non-empty static initializer; this method invokes it on every registered class (via
     * {@link #invokeInstrumentedStaticMethod()}) so static state is re-established deterministically
     * in later iterations.
     *
     * @param iteration the current iteration number (the static initializers are re-run from the
     *     second iteration onward)
     */
    public static void invokeStaticInitializedClasses(int iteration) {
        // reloadStaticInitializedClasses();
        invokeInstrumentedStaticMethod();
    }

    /**
     * Class loader used by the (currently disabled) approach to reload instrumented classes and
     * re-trigger their static initializers. Retained for reference; not used by the active code
     * path.
     */
    private static class ReloadingClassLoader extends URLClassLoader {
        /**
         * Creates a reloading class loader over the given URLs with no parent.
         *
         * @param urls the URLs to load classes from
         */
        public ReloadingClassLoader(URL[] urls) {
            super(urls, null);
        }

        /**
         * Loads (reloads) the class with the given name through this loader.
         *
         * @param className the fully qualified class name to reload
         * @throws ClassNotFoundException if the class cannot be found
         */
        public void reloadClass(String className) throws ClassNotFoundException {
            // TODO: this is not working. Need to figure out why?
            // This method is used to reload a class by its name
            Class<?> clazz = loadClass(className, true);
            if (clazz != null) {
                LOGGER.info("Reloaded class: {}", className);
            } else {
                throw new ClassNotFoundException("Class not found: " + className);
            }
        }
    }

    /**
     * Creates a start static init event without yielding.
     * This marks the beginning of static initialization for a class.
     */
    public static void startStaticInitEventWithoutYield() {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.START_STATIC_INIT_EVENT)
                .taskId(JmcRuntime.currentTask());
        JmcRuntime.updateEvent(builder.build());
    }

    /**
     * Creates an end static init event without yielding.
     * This marks the end of static initialization for a class.
     */
    public static void endStaticInitEventWithoutYield() {
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT)
                .taskId(JmcRuntime.currentTask());
        JmcRuntime.updateEvent(builder.build());
    }

    /**
     * Registers a static ExecutorService field for tracking.
     * Uses reflection to avoid triggering field read instrumentation.
     *
     * @param className the fully qualified class name
     * @param fieldName the name of the static ExecutorService field
     */
    public static void registerStaticExecutorField(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object executorService = field.get(null);

            if (executorService instanceof java.util.concurrent.ExecutorService) {
                registerExecutor((java.util.concurrent.ExecutorService) executorService);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to register static executor field: {}.{}", className, fieldName, e);
        }
    }

    /**
     * Registers an ExecutorService for tracking and automatic shutdown.
     *
     * @param executor the ExecutorService to register
     */
    public static void registerExecutor(java.util.concurrent.ExecutorService executor) {
        // This will be called by TrackExecutors
        JmcRuntimeEvent.Builder builder = new JmcRuntimeEvent.Builder();
        builder.type(JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("executor", executor)
                .param("action", "register");
        JmcRuntime.updateEvent(builder.build());
    }

}
