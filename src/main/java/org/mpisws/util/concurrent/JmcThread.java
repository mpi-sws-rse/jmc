package org.mpisws.util.concurrent;

import org.mpisws.runtime.HaltTaskException;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

public class JmcThread extends Thread {

    public boolean hasTask = false;
    private Long jmcThreadId;

    public JmcThread() {
        this(JmcRuntime.addNewTask());
    }

    public JmcThread(Long jmcThreadId) {
        super();
        this.jmcThreadId = jmcThreadId;
        super.setUncaughtExceptionHandler(this::handleInterrupt);
    }

    public JmcThread(Runnable r, Long jmcThreadId) {
        super(r);
        this.jmcThreadId = jmcThreadId;
        super.setUncaughtExceptionHandler(this::handleInterrupt);
    }

    @Override
    public void run() {
        this.hasTask = true;
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.START_EVENT)
                        .taskId(jmcThreadId)
                        .build();
        JmcRuntime.updateEvent(event);
        JmcRuntime.spawn(jmcThreadId);
        try {
            run1();
        } catch (HaltTaskException e) {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.HALT_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            JmcRuntime.updateEvent(event);
        } finally {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.FINISH_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            JmcRuntime.updateEvent(event);
            JmcRuntime.join(jmcThreadId);
        }
    }

    public void run1() {
        super.run();
    }

    private void handleInterrupt(Thread t, Throwable e) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.HALT_EVENT)
                        .taskId(jmcThreadId)
                        .build();
        JmcRuntime.updateEvent(event);
    }

    public void join1() throws InterruptedException {
        Long requestingTask = JmcRuntime.currentTask();
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.JOIN_REQUEST_EVENT)
                        .taskId(requestingTask)
                        .param("waitingTask", jmcThreadId)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        super.join();
    }
}
