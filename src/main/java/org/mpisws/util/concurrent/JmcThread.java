package org.mpisws.util.concurrent;

import org.mpisws.runtime.HaltTaskException;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

/**
 * This class is a wrapper around the Java Thread class. It is used to intercept the start, finish,
 * and interrupt events of a thread.
 *
 * <p>The goal is to replace all references to Thread with JmcThread in bytecode instrumentation
 */
public class JmcThread extends Thread {

    public boolean hasTask = false;
    private Long jmcThreadId;

    // TODO: extend to all constructors of Thread and handle ThreadGroups, also all join methods
    // Should be a drop in replacement for all possible ways to use Threads

    /** Constructs a new JmcThread object. */
    public JmcThread() {
        this(JmcRuntime.addNewTask());
    }

    /** Constructs a new JmcThread object with the given Runnable. */
    public JmcThread(Runnable r) {
        this(r, JmcRuntime.addNewTask());
    }

    /** Constructs a new JmcThread object with the given JMC thread ID. */
    public JmcThread(Long jmcThreadId) {
        super();
        this.jmcThreadId = jmcThreadId;
        super.setUncaughtExceptionHandler(this::handleInterrupt);
    }

    /** Constructs a new JmcThread object with the given Runnable and JMC thread ID. */
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
        JmcRuntime.yield(jmcThreadId);
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

    @Override
    public void start() {
        super.start();
        JmcRuntime.spawn();
    }

    /** This method is overridden by the user. */
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

    /** Replacing the Thread join to intercept the join Event. */
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
