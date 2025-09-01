package org.mpi_sws.jmc.api;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

public class JmcObject {
    public static void objectWait(Object o) throws InterruptedException {
        objectWait(o, 0);
    }

    public static void objectWait(Object o, long timeoutMillis) throws InterruptedException {
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
        o.wait(timeoutMillis);

        event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WAKEUP_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("object", o)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (Exception e) {
            throw new InterruptedException("Wakeup interrupted: " + e.getMessage());
        }
    }

    public static void objectNotify(Object o) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.NOTIFY_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("object", o)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (Exception e) {
            // Ignore
        }
        o.notify();
    }

    public static void objectNotifyAll(Object o) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.NOTIFY_ALL_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("object", o)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (Exception e) {
            // Ignore
        }
        o.notifyAll();
    }
}
