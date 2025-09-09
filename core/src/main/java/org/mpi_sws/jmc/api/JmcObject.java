package org.mpi_sws.jmc.api;

import org.mpi_sws.jmc.api.util.concurrent.JmcReentrantLock;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

public class JmcObject {
    public static void objectWait(Object o) throws InterruptedException {
        objectWait(o, 0);
    }

    public static void objectWait(Object o, long timeoutMillis) throws InterruptedException {
        JmcReentrantLock lock = JmcRuntimeUtils.getSyncLock(o);
        if (lock == null) {
            throw HaltCheckerException.error(
                    "Object not used in synchronized block: " + o.getClass() + "@" + o.hashCode());
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
                    "Object not used in synchronized block: " + o.getClass() + "@" + o.hashCode());
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
                    "Object not used in synchronized block: " + o.getClass() + "@" + o.hashCode());
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
}
