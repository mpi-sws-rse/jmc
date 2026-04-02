package org.mpi_sws.jmc.api;

import org.mpi_sws.jmc.api.util.concurrent.JmcReentrantLock;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JmcObject {
    public static void objectWait(Object o) throws InterruptedException {
        objectWait(o, 0);
    }

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
