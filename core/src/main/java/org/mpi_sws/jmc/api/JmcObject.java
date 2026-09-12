package org.mpi_sws.jmc.api;

import org.mpi_sws.jmc.api.util.concurrent.JmcReentrantLock;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Static entry points that replace {@link Object}'s monitor and identity methods under JMC.
 *
 * <p>The instrumentation agent rewrites {@code o.wait()/notify()/notifyAll()} into the static {@code
 * objectWait}/{@code objectNotify}/{@code objectNotifyAll} calls here (see the agent's
 * {@code JmcWaitNotifyVisitor}); each reports the corresponding monitor event to the runtime and
 * yields, so {@code TrackWaitNotify} can serialize wait/notify. This class also holds the
 * reflection-based handlers ({@code handleHashCode}, {@code handleToString}, {@code handleEquals},
 * and the {@code TODO} {@code handleClone}/{@code handleFinalize}) that dispatch to the {@code jmc*}
 * methods the agent's {@code JmcNativeMethodVisitor} generates, giving JMC deterministic identity
 * semantics.
 */
public class JmcObject {
    /**
     * Replacement for {@code o.wait()}: waits on the monitor with no timeout.
     *
     * @param o the monitor object to wait on
     * @throws InterruptedException if the wait is interrupted
     */
    public static void objectWait(Object o) throws InterruptedException {
        objectWait(o, 0);
    }

    /**
     * Replacement for {@code o.wait(timeout)}.
     *
     * <p>Looks up the {@link JmcReentrantLock} backing the monitor object (throwing a checker error
     * if {@code o} was not used in a {@code synchronized} block), reports a {@code WAIT_EVENT} and
     * yields — which lets {@code TrackWaitNotify} park this task and release its lock — then
     * re-acquires the lock and reports a {@code WAKEUP_EVENT}. The timeout is currently not modeled.
     *
     * @param o the monitor object to wait on
     * @param timeoutMillis the requested timeout in milliseconds (not modeled)
     * @throws InterruptedException if the wait or wakeup is interrupted
     */
    public static void objectWait(Object o, long timeoutMillis) throws InterruptedException {
        JmcReentrantLock lock = JmcRuntimeUtils.getSyncLock(o);
        if (lock == null) {
            throw HaltCheckerException.error(
                    "Object not used in synchronized block: " + o.getClass() + "@" +handleHashCode(o));
        }
        o = lock.getInstance();
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WAIT_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("object", o)
                        .param("timeout", timeoutMillis)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(event);
        } catch (Exception e) {
            throw new InterruptedException("Wait interrupted: " + e.getMessage());
        }

        lock.lock();

        event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WAKEUP_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("object", o)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(event);
        } catch (Exception e) {
            throw new InterruptedException("Wakeup interrupted: " + e.getMessage());
        }
    }

    /**
     * Replacement for {@code o.notify()}: reports a {@code NOTIFY_EVENT} and yields.
     *
     * <p>Looks up the monitor's {@link JmcReentrantLock} (erroring if the object was not used in a
     * {@code synchronized} block); {@code TrackWaitNotify} then moves one of the object's waiters
     * toward runnable.
     *
     * @param o the monitor object to signal
     */
    public static void objectNotify(Object o) {
        JmcReentrantLock lock = JmcRuntimeUtils.getSyncLock(o);
        if (lock == null) {
            throw HaltCheckerException.error(
                    "Object not used in synchronized block: " + o.getClass() + "@" + handleHashCode(o));
        }
        o = lock.getInstance();
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.NOTIFY_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("object", o)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(event);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Replacement for {@code o.notifyAll()}: reports a {@code NOTIFY_ALL_EVENT} and yields.
     *
     * <p>Looks up the monitor's {@link JmcReentrantLock} (erroring if the object was not used in a
     * {@code synchronized} block); {@code TrackWaitNotify} then moves all of the object's waiters
     * toward runnable.
     *
     * @param o the monitor object to signal
     */
    public static void objectNotifyAll(Object o) {
        JmcReentrantLock lock = JmcRuntimeUtils.getSyncLock(o);
        if (lock == null) {
            throw HaltCheckerException.error(
                    "Object not used in synchronized block: " + o.getClass() + "@" + handleHashCode(o));
        }
        o = lock.getInstance();
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.NOTIFY_ALL_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("object", o)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(event);
        } catch (Exception e) {
            // Ignore
        }
    }

    // ========== Native Object Method Handlers ==========

    /**
     * Handles hashCode() calls - invokes jmcHashCode() via reflection if it exists,
     * otherwise calls obj.hashCode()
     */
    public static int handleHashCode(Object obj) {
        if (obj == null) return 0;

        try {
            Method method = obj.getClass().getMethod("jmcHashCode");
            method.setAccessible(true);
            return (int) method.invoke(obj);
        } catch (NoSuchMethodException e) {
            return obj.hashCode();
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke jmcHashCode", e);
        }
    }

    /**
     * Produces the default {@code Object.toString} form ({@code ClassName@hexHashCode}) using JMC's
     * {@link #handleHashCode} so the hash is deterministic. Used as the fallback by {@link
     * #handleToString} and by the generated {@code jmcToString} methods.
     *
     * @param obj the object to render
     * @return the {@code ClassName@hexHashCode} representation
     */
    public static String toString(Object obj) {
        return obj.getClass().getName() + "@" + Integer.toHexString(handleHashCode(obj));
    }

    /**
     * Handles toString() calls - invokes jmcToString() via reflection if it exists,
     * otherwise calls obj.toString()
     */
    public static String handleToString(Object obj) {
        if (obj == null) return "null";

        try {
            Method method = obj.getClass().getMethod("jmcToString");
            method.setAccessible(true);
            return (String) method.invoke(obj);
        } catch (NoSuchMethodException e) {
            return toString(obj);

        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke jmcToString", e);
        }
    }

    /**
     * Handles equals(Object) calls - invokes jmcEquals(Object) via reflection if it exists,
     * otherwise calls obj.equals(other)
     */
    public static boolean handleEquals(Object obj, Object other) {
        if (obj == null) return other == null;

        try {
            Method method = obj.getClass().getMethod("jmcEquals", Object.class);
            method.setAccessible(true);
            return (boolean) method.invoke(obj, other);
        } catch (NoSuchMethodException e) {
            return obj.equals(other);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke jmcEquals", e);
        }
    }




    /**
     * Handles clone() calls - invokes clone__jmc__() via reflection
     * TODO :: Fix This
     */
    public static Object handleClone(Object obj) {
        if (obj == null) return null;

        try {
            Method method = obj.getClass().getMethod("clone__jmc__");
            method.setAccessible(true);
            return method.invoke(obj);
        } catch (NoSuchMethodException e) {
            // Try calling clone() directly (may fail if not Cloneable)
            try {
                Method cloneMethod = obj.getClass().getMethod("clone");
                cloneMethod.setAccessible(true);
                return cloneMethod.invoke(obj);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to invoke clone", ex);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke clone__jmc__", e);
        }
    }

    /**
     * Handles finalize() calls - invokes finalize__jmc__() via reflection
     * TODO :: Fix This
     */
    public static void handleFinalize(Object obj) {
        if (obj == null) return;
        try {
            Method method = obj.getClass().getDeclaredMethod("finalize__jmc__");
            method.setAccessible(true);
            method.invoke(obj);
        } catch (NoSuchMethodException e) {
            // No custom finalize, do nothing
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke finalize__jmc__", e);
        }
    }

}
