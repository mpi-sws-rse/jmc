package org.mpisws.jmc.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

/**
 * This class is a wrapper around the Java Thread class. It is used to intercept the start, finish,
 * and interrupt events of a thread.
 *
 * <p>The goal is to replace all references to Thread with JmcThread in bytecode instrumentation
 *
 * <p>The method to be overridden is now run1 and similarly the method to join is join1
 */
public class JmcThread extends Thread {

    private static Logger LOGGER = LogManager.getLogger(JmcThread.class);

    private Long jmcThreadId;
    private Long createdBy;

    // TODO: extend to all constructors of Thread and handle ThreadGroups, also all join methods
    //      Should be a drop in replacement for all possible ways to use Threads

    /**
     * Constructs a new JmcThread object.
     */
    public JmcThread() {
        this(JmcRuntime.addNewTask());
    }

    /**
     * Constructs a new JmcThread object with the given Runnable.
     */
    public JmcThread(Runnable r) {
        this(r, JmcRuntime.addNewTask());
    }

    /**
     * Constructs a new JmcThread object with the given JMC thread ID.
     */
    public JmcThread(Long jmcThreadId) {
        super();
        this.jmcThreadId = jmcThreadId;
        this.createdBy = JmcRuntime.currentTask();
        super.setUncaughtExceptionHandler(this::handleInterrupt);
        LOGGER = LogManager.getLogger(JmcThread.class.getName() + " Task=" + jmcThreadId);
    }

    /**
     * Constructs a new JmcThread object with the given Runnable and JMC thread ID.
     */
    public JmcThread(Runnable r, Long jmcThreadId) {
        super(r);
        this.jmcThreadId = jmcThreadId;
        this.createdBy = JmcRuntime.currentTask();
        super.setUncaughtExceptionHandler(this::handleInterrupt);
        LOGGER = LogManager.getLogger(JmcThread.class.getName() + " Task=" + jmcThreadId);
    }

    /**
     * Returns the task ID of this thread.
     *
     * @return The task ID of this thread.
     */
    public Long getTaskId() {
        return jmcThreadId;
    }

    @Override
    public void run() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.START_EVENT)
                        .taskId(jmcThreadId)
                        .param("startedBy", createdBy)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to start task: {}", e.getMessage());
        }
        JmcRuntime.yield(jmcThreadId);
        try {
            run1();
        } catch (HaltTaskException e) {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.HALT_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            try {
                JmcRuntime.updateEvent(event);
            } catch (HaltTaskException ex) {
                LOGGER.error("Failed to halt task : {}", ex.getMessage());
            }
        } finally {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.FINISH_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            try {
                JmcRuntime.updateEvent(event);
            } catch (HaltTaskException e) {
                LOGGER.error("Failed to finish task : {}", e.getMessage());
            }
            JmcRuntime.join(jmcThreadId);
        }
    }

    /**
     * Used to run just the function in a wrapped thread and not as a separate thred.
     *
     * <p>Used internally by the Executor service that will invoke threads in a larger thread
     * context.
     */
    public void runWithoutJoin() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.START_EVENT)
                        .taskId(jmcThreadId)
                        .param("startedBy", createdBy)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to start task: {}", e.getMessage());
        }
        JmcRuntime.yield(jmcThreadId);
        try {
            run1();
        } catch (HaltTaskException e) {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.HALT_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            try {
                JmcRuntime.updateEvent(event);
            } catch (HaltTaskException ex) {
                LOGGER.error("Failed to halt task : {}", ex.getMessage());
            }
        } finally {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.FINISH_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            try {
                JmcRuntime.updateEvent(event);
            } catch (HaltTaskException e) {
                LOGGER.error("Failed to finish task : {}", e.getMessage());
            }
        }
    }

    @Override
    public void start() {
        Long taskId = JmcRuntime.currentTask();
        JmcRuntime.pause(taskId);
        super.start();
        JmcRuntime.wait(taskId);
    }

    /**
     * This method is overridden by the user.
     */
    public void run1() throws HaltTaskException {
        super.run();
    }

    private void handleInterrupt(Thread t, Throwable e) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.HALT_EVENT)
                        .taskId(jmcThreadId)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (HaltTaskException ex) {
            LOGGER.error("Failed to halt task on interrupt : {}", ex.getMessage());
        }
        LOGGER.info("thread " + t.getName() + " interrupted with exception: " + e.getMessage());
    }

    /**
     * Replacing the thread join to intercept the join Event
     *
     * @throws InterruptedException when the underlying join call fails
     */
    public void join1() throws InterruptedException {
        join1(0L);
    }

    /**
     * Replacing the Thread join to intercept the join Event.
     */
    public void join1(Long millis) throws InterruptedException {
        Long requestingTask = JmcRuntime.currentTask();
        RuntimeEvent requestEvent =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.JOIN_REQUEST_EVENT)
                        .taskId(requestingTask)
                        .param("waitingTask", jmcThreadId)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(requestEvent);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to join task : {}", e.getMessage());
        }
        super.join(millis);
        RuntimeEvent completedEvent =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.JOIN_COMPLETE_EVENT)
                        .taskId(requestingTask)
                        .param("joinedTask", jmcThreadId)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(completedEvent);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to complete join task : {}", e.getMessage());
        }
    }
}
