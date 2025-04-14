package org.mpisws.jmc.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.strategies.SchedulingStrategy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SynchronousQueue;

/**
 * The scheduler is responsible for managing the execution of threads.
 *
 * <p>The scheduler uses the strategy to decide which thread to Schedule. To do so, it instantiates
 * a separate SchedulerThread that is blocked whenever other threads are running and unblocked only
 * when it needs to pick the next thread to schedule.
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
        this.currentTask = 0L;
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
     * Initializes the strategy for a new iteration.
     *
     * @param iteration the number of the iteration
     */
    public void initIteration(int iteration) throws HaltCheckerException {
        strategy.initIteration(iteration);
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
     * Performs the scheduling choice (instance of {@link SchedulingChoice}) indicated. Either
     * resuming the task, stopping the task or stopping all tasks.
     *
     * @param choice The scheduling choice to make.
     */
    protected void scheduleTask(SchedulingChoice choice) {
        if (choice.isBlockExecution()) {
            taskManager.stopAll();
        } else if (choice.isBlockTask()) {
            taskManager.error(choice.getTaskId(), new HaltTaskException(choice.getTaskId()));
        } else {
            Long taskId = choice.getTaskId();
            setCurrentTask(taskId);
            try {
                LOGGER.debug("Resuming task: {}", taskId);
                taskManager.resume(taskId);
            } catch (TaskNotExists e) {
                LOGGER.info("Resuming a non existent task: {}", e.getMessage());
                // TODO :: For debugging
                /*System.out.println("[Scheduler debug] : Resuming a non existent task: " + e.getMessage());*/
                System.exit(1);
            }
        }
    }

    /**
     * Updates the event in the scheduling strategy.
     *
     * @param event the event to be updated
     */
    public void updateEvent(RuntimeEvent event) throws HaltTaskException {
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

    /**
     * Pauses the task with the given ID and yields the control to the scheduler.
     *
     * <p>The call is non-blocking and returns immediately.
     *
     * @param taskId the ID of the task to be paused
     * @return a future that completes when the task is resumed
     * @throws TaskAlreadyPaused if the task is already paused
     */
    public CompletableFuture<Boolean> yield(Long taskId) throws TaskAlreadyPaused {
        CompletableFuture<Boolean> future = taskManager.pause(taskId);
        synchronized (currentTaskLock) {
            currentTask = null;
        }
        // Release the scheduler thread
        LOGGER.debug("Enabling scheduler thread.");
        schedulerThread.enable();
        return future;
    }

    /** Resets the TaskManager and the scheduling strategy for a new iteration. */
    public void resetIteration(int iteration) {
        strategy.resetIteration(iteration);
    }

    /** Shuts down the scheduler. */
    public void shutdown() {
        schedulerThread.shutdown();
        strategy.teardown();
    }

    /** The SchedulerThread class is responsible for scheduling the tasks. */
    private static class SchedulerThread extends Thread {

        private static final Logger LOGGER = LogManager.getLogger(SchedulerThread.class.getName());

        /** The scheduler instance. */
        private final Scheduler scheduler;

        /** The scheduling strategy used by the scheduler. */
        private final SchedulingStrategy strategy;

        /**
         * A queue used to enable the scheduler thread. Adding a boolean value to the queue enables
         * the scheduler to run one iteration, while adding a true value shuts down the scheduler.
         */
        private final SynchronousQueue<Boolean> enablingQueue;

        /**
         * Constructs a new SchedulerThread object.
         *
         * @param scheduler the scheduler instance
         * @param strategy the scheduling strategy
         */
        public SchedulerThread(Scheduler scheduler, SchedulingStrategy strategy) {
            this.scheduler = scheduler;
            this.strategy = strategy;
            this.enablingQueue = new SynchronousQueue<>();
        }

        /** Enables the scheduler. */
        public void enable() {
            try {
                enablingQueue.put(false);
            } catch (InterruptedException e) {
                LOGGER.error("Enabling the scheduler thread was interrupted: {}", e.getMessage());
            }
        }

        /** Shuts down the scheduler. */
        public void shutdown() {
            try {
                enablingQueue.put(true);
            } catch (InterruptedException e) {
                LOGGER.error(
                        "Shutting down the scheduler thread was interrupted: {}", e.getMessage());
            }
        }

        /** The main loop of the scheduler thread. */
        @Override
        public void run() {
            LOGGER.info("Scheduler thread started.");
            while (true) {
                // Wait for the scheduler to be enabled
                try {
                    Boolean shutdown = enablingQueue.take();
                    if (shutdown) {
                        LOGGER.info("Shutting down scheduler thread.");
                        break;
                    }
                    LOGGER.debug("Scheduler thread enabled.");
                    SchedulingChoice nextTask = strategy.nextTask();
                    // TODO :: For debugging
                    /*System.out.println("[Scheduler debug] : The next task to be scheduled is: " + nextTask);*/
                    if (nextTask != null) {
                        scheduler.scheduleTask(nextTask);
                    } else {
                        LOGGER.debug("No task to schedule.");
                    }
                } catch (Exception e) {
                    LOGGER.error("Scheduler thread threw an exception: {}", e.getMessage());
                    break;
                }
            }
            LOGGER.info("Scheduler thread finished.");
        }
    }
}
