package org.mpi_sws.jmc.runtime.scheduling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.runtime.*;
import org.mpi_sws.jmc.strategies.ReplayableSchedulingStrategy;
import org.mpi_sws.jmc.strategies.SchedulingStrategy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

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

    /**
     * The thread manager used to manage the thread states.
     */
    private TaskManager taskManager;

    /**
     * The scheduling strategy used to decide which thread to schedule.
     */
    private final SchedulingStrategy strategy;

    /**
     * The ID of the current thread. Protected by the lock for accesses to read and write
     */
    private Long currentTask;

    private final Object currentTaskLock = new Object();

    /**
     * The scheduler thread instance.
     */
    private final SchedulerThread schedulerThread;

    private boolean stopAllMode = false;

    /**
     * Constructs a new Scheduler object.
     *
     * @param strategy the scheduling strategy
     */
    public Scheduler(
            SchedulingStrategy strategy, int schedulerTries, long schedulerTrySleepTimeNanos) {
        this.strategy = strategy;
        this.schedulerThread =
                new SchedulerThread(this, strategy, schedulerTries, schedulerTrySleepTimeNanos);
        this.currentTask = 0L;
    }

    /**
     * Starts the scheduler thread.
     */
    public void start() {
        schedulerThread.start();
    }

    /**
     * Initializes the scheduler with the task manager and the main thread.
     *
     * @param taskManager the task manager
     * @param mainTaskId  the ID of the main thread
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
    public void initIteration(int iteration, JmcModelCheckerReport report)
            throws HaltCheckerException {
        // Ask the tastManager if the all thread are finished. If not block it
        // clear the threads state
        strategy.initIteration(iteration, report);
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
    protected <T extends SchedulingChoiceValue> void scheduleTask(SchedulingChoice<T> choice) {
        if (choice.isBlockExecution()) {
            LOGGER.debug("Stopping all tasks.");
            startStopAllMode();
        } else if (choice.isBlockTask()) {
            Long taskId = choice.getTaskId();
            setCurrentTask(taskId);
            taskManager.error(taskId, HaltTaskException.blocked(taskId));
        } else {
            Long taskId = choice.getTaskId();
            if (taskId == null) {
                LOGGER.error("Resuming a task with null ID.");
                throw HaltExecutionException.error("Resuming a task with null ID.");
            }
            setCurrentTask(taskId);
            try {
                LOGGER.debug("Resuming task: {}", taskId);
                if (taskId == null) {
                    LOGGER.error("Task ID is null, cannot resume task.");
                    throw HaltExecutionException.error("Task ID is null, cannot resume task.");
                }
                if (choice.getValue() != null) {
                    taskManager.resume(taskId, choice.getValue());
                } else {
                    taskManager.resume(taskId);
                }
            } catch (TaskNotExists e) {
                LOGGER.error("Resuming a non existent task: {}", e.getMessage());
                throw HaltExecutionException.error(e.getMessage());
            }
        }
    }

    private void startStopAllMode() {
        stopAllMode = true;
        doNextStop();
    }

    private void doNextStop() {
        Long taskId = taskManager.doNextStop();
        if (taskId == -1L) {
            LOGGER.debug("No pausable tasks remain in stop-all mode; exiting.");
            stopAllMode = false;
            return;
        }
        setCurrentTask(taskId);
        taskManager.stopTask(taskId);
        if (taskId == 1L) {
            // Main task stopped, exit stop all mode
            stopAllMode = false;
            LOGGER.debug("Exiting stop all mode.");
        }
    }

    /**
     * Updates the event in the scheduling strategy.
     *
     * @param event the event to be updated
     */
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException {
        if (isInStopAllMode()) {
            return;
        }
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
    public CompletableFuture<?> yield() throws TaskAlreadyPaused {
        CompletableFuture<?> future;
        synchronized (currentTaskLock) {
            future = taskManager.pause(currentTask);
            currentTask = null;
        }
        // Release the scheduler thread
        LOGGER.debug("Enabling scheduler thread.");
        schedulerThread.enable();
        return future;
    }

    public void yieldWithoutPausing() {
        synchronized (currentTaskLock) {
            currentTask = null;
        }
        // Release the scheduler thread
        LOGGER.debug("Enabling scheduler thread.");
        schedulerThread.enable();
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
    public CompletableFuture<?> yield(Long taskId) throws TaskAlreadyPaused {
        CompletableFuture<?> future = taskManager.pause(taskId);
        synchronized (currentTaskLock) {
            currentTask = null;
        }
        // Release the scheduler thread
        LOGGER.debug("Enabling scheduler thread.");
        schedulerThread.enable();
        return future;
    }

    /**
     * Resets the TaskManager and the scheduling strategy for a new iteration.
     */
    public void resetIteration(int iteration) {
        strategy.resetIteration(iteration);
    }

    public void recordTrace() {
        if (strategy instanceof ReplayableSchedulingStrategy) {
            try {
                ((ReplayableSchedulingStrategy) strategy).recordTrace();
            } catch (JmcCheckerException e) {
                LOGGER.error("Failed to record trace: {}", e.getMessage());
            }
        } else {
            LOGGER.warn("Recording trace is not supported by the current scheduling strategy");
        }
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown(JmcModelCheckerReport report) {
        schedulerThread.shutdown();
        strategy.teardown(report);
    }

    public boolean isInStopAllMode() {
        return stopAllMode;
    }

    /**
     * When the strategy returns no choice but tasks are paused on the scheduler, resume one so
     * exploration can continue. This avoids a global stall when every live task is blocked in
     * {@link TaskManager#pause(Long)} waiting for a scheduling decision.
     */
    Long resumeBlockedTaskIfNeeded() {
        for (Long taskId : taskManager.findTasksWithStatus(TaskManager.TaskState.BLOCKED)) {
            LOGGER.debug("Resuming blocked task {} because the strategy returned no choice.", taskId);
            scheduleTask(SchedulingChoice.task(taskId));
            return taskId;
        }
        return null;
    }

    /**
     * The SchedulerThread class is responsible for scheduling the tasks.
     */
    private static class SchedulerThread extends Thread {

        private static final Logger LOGGER = LogManager.getLogger(SchedulerThread.class.getName());

        /**
         * The scheduler instance.
         */
        private final Scheduler scheduler;

        /**
         * The scheduling strategy used by the scheduler.
         */
        private final SchedulingStrategy strategy;

        /**
         * A queue used to enable the scheduler thread. Adding a boolean value to the queue enables
         * the scheduler to run one iteration, while adding a true value shuts down the scheduler.
         */
        private final LinkedBlockingQueue<Boolean> enablingQueue;

        private final int schedulerTries;

        private final long schedulerTrySleepTimeNanos;

        /**
         * Constructs a new SchedulerThread object.
         *
         * @param scheduler the scheduler instance
         * @param strategy  the scheduling strategy
         */
        public SchedulerThread(
                Scheduler scheduler,
                SchedulingStrategy strategy,
                int schedulerTries,
                long schedulerTrySleepTimeNanos) {
            this.scheduler = scheduler;
            this.strategy = strategy;
            this.enablingQueue = new LinkedBlockingQueue<>();
            this.schedulerTries = schedulerTries;
            this.schedulerTrySleepTimeNanos = schedulerTrySleepTimeNanos;
        }

        /**
         * Enables the scheduler.
         */
        public void enable() {
            try {
                enablingQueue.put(false);
            } catch (InterruptedException e) {
                LOGGER.error("Enabling the scheduler thread was interrupted: {}", e.getMessage());
            }
        }

        /**
         * Shuts down the scheduler.
         */
        public void shutdown() {
            try {
                enablingQueue.put(true);
            } catch (InterruptedException e) {
                LOGGER.error(
                        "Shutting down the scheduler thread was interrupted: {}", e.getMessage());
            }
        }

        /**
         * The main loop of the scheduler thread.
         */
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
                    // Repeat until the task is not null. Error out after trying x times.
                    // It is possible that the scheduler is enabled but no task is available.
                    // The solution is to just wait for something to become available. and throw an
                    // error otherwise.

                    if (scheduler.isInStopAllMode()) {
                        scheduler.doNextStop();
                        continue;
                    }

                    SchedulingChoice<?> nextTask = null;
                    for (int i = 0; i < schedulerTries; i++) {
                        nextTask = strategy.nextTask();
                        if (nextTask != null) {
                            break;
                        }
                        if (schedulerTrySleepTimeNanos > 0) {
                            Thread.sleep(schedulerTrySleepTimeNanos);
                        }
                    }
                    if (nextTask != null) {
                        scheduler.scheduleTask(nextTask);
                    } else {
                        Long blockedTask = scheduler.resumeBlockedTaskIfNeeded();
                        if (blockedTask == null) {
                            LOGGER.error("No task to schedule.");
                        }
                    }
                } catch (HaltExecutionException e) {
                    LOGGER.debug("Scheduler thread halt: {}", e.getMessage());
                } catch (Exception e) {
                    LOGGER.error("Scheduler thread threw an exception: {}", e.getMessage(), e);
                    break;
                }
            }
            LOGGER.info("Scheduler thread finished.");
        }
    }
}
