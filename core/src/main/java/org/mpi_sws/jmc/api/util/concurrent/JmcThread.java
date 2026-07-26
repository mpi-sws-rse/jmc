package org.mpi_sws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

/**
 * This class is a wrapper around the Java Thread class - {@link java.lang.Thread}. It is used to
 * intercept the start, finish, and interrupt events of a thread.
 *
 * <p>The goal is to replace all references to Thread with JmcThread in bytecode instrumentation.
 *
 * <p>The method to be overridden is now run1 and similarly the method to join is join1.
 */
public class JmcThread extends Thread {

    /** Logger for this thread; re-bound with the task id once one is assigned. */
    private static Logger LOGGER = LogManager.getLogger(JmcThread.class);

    /** JMC task id identifying this thread to the runtime (may be {@code null} for a wrapper). */
    private final Long jmcThreadId;
    /** Task id of the task that created this thread (recorded as {@code startedBy} in the start event). */
    private final Long createdBy;

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
     * Private constructor for wrapping an existing thread/runnable.
     *
     * <p>When {@code initialize} is {@code true} a new runtime task is allocated and the interrupt
     * handler installed; when {@code false} no task is created (used by {@link #currentThread()} to
     * wrap a non-JMC thread) — in which case JMC methods must not be called on the result.
     *
     * @param r the runnable to wrap
     * @param initialize whether to allocate a JMC task and install the interrupt handler
     */
    private JmcThread(Runnable r, boolean initialize) {
        super(r);
        if (initialize) {
            this.jmcThreadId = JmcRuntime.addNewTask();
            this.createdBy = JmcRuntime.currentTask();
            super.setUncaughtExceptionHandler(this::handleInterrupt);
        } else {
            this.jmcThreadId = null;
            this.createdBy = null;
        }
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
    }

    /**
     * Returns the current thread as a {@link JmcThread}.
     *
     * <p>If the current thread is already a {@code JmcThread} it is returned directly; otherwise it is
     * wrapped in a non-initialized {@code JmcThread} (no runtime task is allocated — JMC methods must
     * not be called on such a wrapper).
     *
     * @return the current thread as a {@code JmcThread}
     */
    public static JmcThread currentThread() {
        Thread t = Thread.currentThread();
        if (t instanceof JmcThread) {
            return (JmcThread) t;
        } else {
            return new JmcThread(t, false);
        }
    }

    /**
     * Returns the task ID of this thread.
     *
     * @return The task ID of this thread.
     */
    public Long getTaskId() {
        return jmcThreadId;
    }

    /**
     * JMC harness for a started thread.
     *
     * <p>Reports a {@code START_EVENT} (with {@code startedBy = createdBy}), yields under this
     * thread's task id so the runtime schedules it deterministically, runs the user body {@link
     * #run1()}, and finally — in a {@code finally} — reports a {@code FINISH_EVENT} and joins the
     * task. Re-execution and blocked-task signals are logged rather than propagated. This method is
     * the entry point the JVM invokes when {@link #start()} launches the thread; user code lives in
     * {@code run1}.
     */
    @Override
    public void run() {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.START_EVENT)
                        .taskId(jmcThreadId)
                        .param("startedBy", createdBy)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to start task: {}", e.getMessage());
        }
        try {
            JmcRuntime.yield(jmcThreadId);
            run1();
        } catch (Exception e) {
            if (e instanceof HaltExecutionException && ((HaltExecutionException) e).isReexecutionNeeded()) {
                LOGGER.debug("Re-execution needed, throwing HaltExecutionException");
            } else if (e instanceof HaltTaskException && ((HaltTaskException) e).isBlocked()) {
                LOGGER.debug("Blocked task execution, throwing HaltTaskException");
            } else {
                LOGGER.error("Exception running the thread: {}", e.getMessage());
            }
        } finally {
            event =
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.FINISH_EVENT)
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
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.START_EVENT)
                        .taskId(jmcThreadId)
                        .param("startedBy", createdBy)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to start task: {}", e.getMessage());
        }
        try {
            JmcRuntime.yield(jmcThreadId);
            run1();
        } catch (HaltTaskException e) {
            event =
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.HALT_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            try {
                JmcRuntime.updateEvent(event);
            } catch (HaltTaskException ex) {
                LOGGER.error("Failed to halt task (runWithoutJoin) : {}", ex.getMessage());
            }
        } finally {
            event =
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.FINISH_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            try {
                JmcRuntime.updateEvent(event);
            } catch (HaltTaskException e) {
                LOGGER.error("Failed to finish task (runWithoutJoin) : {}", e.getMessage());
            }
        }
    }

    /**
     * Starts the thread under JMC's control.
     *
     * <p>Pauses the current (starting) task, calls {@link Thread#start()} to launch the new JVM
     * thread, and waits — handing control to the runtime so the newly started task is scheduled
     * deterministically rather than racing the parent.
     */
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

    /**
     * Uncaught-exception handler installed on this thread: reports a {@code HALT_EVENT} for the task
     * so the runtime records that it was interrupted, then logs the exception.
     *
     * @param t the thread that threw
     * @param e the uncaught throwable
     */
    private void handleInterrupt(Thread t, Throwable e) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.HALT_EVENT)
                        .taskId(jmcThreadId)
                        .build();
        try {
            JmcRuntime.updateEvent(event);
        } catch (HaltTaskException ex) {
            LOGGER.error("Failed to halt task on interrupt : {}", ex.getMessage());
        }
        LOGGER.info("thread {} interrupted with exception: {}", t.getName(), e.getMessage());
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
        JmcRuntimeEvent requestEvent =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT)
                        .taskId(requestingTask)
                        .param("waitingTask", jmcThreadId)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(requestEvent);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to join task : {}", e.getMessage());
        }
        super.join(millis);
        JmcRuntimeEvent completedEvent =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.JOIN_COMPLETE_EVENT)
                        .taskId(requestingTask)
                        .param("joinedTask", jmcThreadId)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(completedEvent);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to complete join task : {}", e.getMessage());
        }
    }

    /**
     * Returns a string representation of this thread, including the
     * thread's name, priority, and thread group.
     *
     * @return a string representation of this thread.
     */
    @Override
    public String toString() {
        return "JmcThread-" + jmcThreadId;
    }
}
