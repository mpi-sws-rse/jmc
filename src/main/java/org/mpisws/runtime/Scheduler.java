package org.mpisws.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.strategies.SchedulingStrategy;

import java.util.concurrent.CompletableFuture;

/**
 * The scheduler is responsible for managing the execution of threads.
 *
 * <p>The scheduler uses the strategy to decide which thread to Schedule. To do so, it instantiates
 * a separate SchedulerThread that is blocked whenever other threads are running and unblocked only
 * when it need to pick the next thread to schedule.
 *
 * <p>To interact with the scheduler, invoke the {@link Scheduler#yield()} which will defer control
 * the scheduler thread.
 */
public class Scheduler {

    private static final Logger LOGGER = LogManager.getLogger(Scheduler.class.getName());

    /** The thread manager used to manage the thread states. */
    private TaskManager taskManager;

    /** The scheduling strategy used to decide which thread to schedule. */
    private final SchedulingStrategy strategy;

    /** The ID of the current thread. Protected by the lock for accesses to read and write */
    private Long currentTask;

    private final Object currentTaskLock = new Object();

    /** The scheduler thread instance. */
    private final SchedulerThread schedulerThread;

    /**
     * Constructs a new Scheduler object.
     *
     * @param strategy the scheduling strategy
     */
    public Scheduler(SchedulingStrategy strategy) {
        this.strategy = strategy;
        this.schedulerThread = new SchedulerThread(this, strategy);
    }

    /** Starts the scheduler thread. */
    public void start() {
        schedulerThread.start();
    }

    /**
     * Initializes the scheduler with the task manager and the main thread.
     *
     * @param taskManager the task manager
     * @param mainTaskId the ID of the main thread
     */
    public void init(TaskManager taskManager, Long mainTaskId) {
        this.taskManager = taskManager;
        this.setCurrentTask(mainTaskId);
    }

    /**
     * Returns the ID of the current task.
     *
     * @return the ID of the current task
     */
    public Long currentTask() {
        synchronized (currentTaskLock) {
            return currentTask;
        }
    }

    /**
     * Sets the ID of the current task.
     *
     * @param taskId the ID of the current task
     */
    protected void setCurrentTask(Long taskId) {
        synchronized (currentTaskLock) {
            currentTask = taskId;
        }
    }

    /**
     * Schedules the task with the given ID. The task is resumed using the TaskManager. Exits the
     * program if the task does not exist. (Should never happen)
     *
     * @param taskId the ID of the task to be scheduled
     */
    protected void scheduleTask(Long taskId) {
        setCurrentTask(taskId);
        try {
            LOGGER.debug("Resuming task: {}", taskId);
            taskManager.resume(taskId);
        } catch (TaskNotExists e) {
            LOGGER.error("Resuming a non existent task: {}", e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Updates the event in the scheduling strategy.
     *
     * @param event the event to be updated
     */
    public void updateEvent(RuntimeEvent event) {
        strategy.updateEvent(event);
    }

    /**
     * Pauses the current task and yields the control to the scheduler.
     *
     * <p>The call is non-blocking and returns immediately.
     *
     * @return a future that completes when the task is resumed
     * @throws TaskAlreadyPaused if the current task is already paused
     */
    public CompletableFuture<Boolean> yield() throws TaskAlreadyPaused {
        CompletableFuture<Boolean> future;
        synchronized (currentTaskLock) {
            future = taskManager.pause(currentTask);
            currentTask = null;
        }
        // Release the scheduler thread
        LOGGER.debug("Enabling scheduler thread.");
        schedulerThread.enable();
        return future;
    }

    /** Resets the TaskManager and the scheduling strategy for a new iteration. */
    public void endIteration() {
        strategy.reset();
    }

    /** Shuts down the scheduler. */
    public void shutdown() {
        schedulerThread.shutdown();
    }

    /** The SchedulerThread class is responsible for scheduling the tasks. */
    private static class SchedulerThread extends Thread {

        private static final Logger LOGGER = LogManager.getLogger(SchedulerThread.class.getName());

        /** The scheduler instance. */
        private final Scheduler scheduler;

        /** The scheduling strategy used by the scheduler. */
        private final SchedulingStrategy strategy;

        /**
         * The future that is completed when the scheduler is enabled. Protected by a lock. Every
         * time the scheduler is enabled a new future is created.
         */
        private CompletableFuture<Boolean> future;

        private final Object futureLock = new Object();

        /**
         * Constructs a new SchedulerThread object.
         *
         * @param scheduler the scheduler instance
         * @param strategy the scheduling strategy
         */
        public SchedulerThread(Scheduler scheduler, SchedulingStrategy strategy) {
            this.scheduler = scheduler;
            this.strategy = strategy;
            this.future = new CompletableFuture<>();
        }

        /** Enables the scheduler. */
        public void enable() {
            synchronized (futureLock) {
                future.complete(false);
            }
        }

        /** Shuts down the scheduler. */
        public void shutdown() {
            synchronized (futureLock) {
                future.complete(true);
            }
        }

        /** The main loop of the scheduler thread. */
        @Override
        public void run() {
            LOGGER.info("Scheduler thread started.");
            while (true) {
                // Wait for the scheduler to be enabled
                CompletableFuture<Boolean> curFuture;
                synchronized (futureLock) {
                    curFuture = future;
                }
                try {
                    Boolean shutdown = curFuture.join();
                    if (shutdown) {
                        LOGGER.info("Shutting down scheduler thread.");
                        break;
                    }
                    Long nextTask = strategy.nextTask();
                    if (nextTask != null) {
                        scheduler.scheduleTask(nextTask);
                    } else {
                        LOGGER.debug("No task to schedule.");
                    }
                } catch (Exception e) {
                    LOGGER.error("Scheduler thread threw an exception: {}", e.getMessage());
                    break;
                } finally {
                    // Reset the future to wait for the next iteration
                    synchronized (futureLock) {
                        future = new CompletableFuture<>();
                    }
                }
            }
            LOGGER.info("Scheduler thread finished.");
        }
    }
}
