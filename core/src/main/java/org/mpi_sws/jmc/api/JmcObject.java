package org.mpi_sws.jmc.api;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

public class JmcObject {
    public void objectWait(long timeoutMillis) throws InterruptedException {
        JmcRuntimeEvent event = new JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.WAIT_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", this)
                .param("timeout", timeoutMillis)
                .build();
        try {
            JmcRuntime.updateEventAndYield(event);
        } catch (Exception e) {
            throw new InterruptedException("Wait interrupted: " + e.getMessage());
        }
        this.wait(timeoutMillis);

        event = new JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.WAKEUP_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", this)
                .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (Exception e) {
            throw new InterruptedException("Wakeup interrupted: " + e.getMessage());
        }
    }

    public void objectNotify() {
        JmcRuntimeEvent event = new JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.NOTIFY_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", this)
                .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (Exception e) {
            // Ignore
        }
        this.notify();
    }

    public void objectNotifyAll() {
        JmcRuntimeEvent event = new JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.NOTIFY_ALL_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("object", this)
                .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (Exception e) {
            // Ignore
        }
        this.notifyAll();
    }
}
