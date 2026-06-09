package org.mpi_sws.jmc.runtime;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.runtime.scheduling.Scheduler;
import org.mpi_sws.jmc.strategies.JmcReplayUnsupported;
import org.mpi_sws.jmc.strategies.ReplayableSchedulingStrategy;
import org.mpi_sws.jmc.strategies.SchedulingStrategy;
import org.mpi_sws.jmc.strategies.tracker.TrackExecutors;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * The Runtime environment complete with a scheduler and configuration options used by the model
 * checker.
 *
 * <p>Calls to the runtime are made by the instrumented byte code. These calls are used to record
 * events occurring during the execution of tasks or allow for scheduling changes. For example, the
 * runtime can be used to record thread creation and deletion.
 *
 * <p>The runtime is a static class that stores minimal states and delegates calls to the {@link
 * Scheduler} which retains all the state.
 */
public class JmcRuntime {

    /** Logger used to trace runtime setup, task switching, and event handling. */
    private static final Logger LOGGER = LogManager.getLogger(JmcRuntime.class);

    /**
     * Owns all per-task state (state machine and pausing futures).
     *
     * <p>Created once and reused across iterations; reset by {@link #resetIteration(int)} and {@link
     * #tearDown(JmcModelCheckerReport)}.
     */
    private static final TaskManager taskManager = new TaskManager();

    /**
     * The scheduler that owns the scheduler thread and the configured {@link SchedulingStrategy}.
     *
     * <p>Instantiated in {@link #setup(JmcRuntimeConfiguration)} or {@link
     * #setupReplay(JmcRuntimeConfiguration)} and shut down in {@link
     * #tearDown(JmcModelCheckerReport)}.
     */
    private static Scheduler scheduler;

    /**
     * The active runtime configuration.
     *
     * <p>Set during setup and read by {@link #initIteration(int, JmcModelCheckerReport)} to configure
     * the scheduler and the strategy.
     */
    private static JmcRuntimeConfiguration config;

    /**
     * Sets up the runtime with the given configuration.
     *
     * @param config the configuration (instance of {@link JmcRuntimeConfiguration})
     */
    public static void setup(JmcRuntimeConfiguration config) {
        LOGGER.debug("Setting up!");
        JmcRuntime.config = config;
        scheduler =
                new Scheduler(
                        config.getStrategy(),
                        config.getSchedulerTries(),
                        config.getSchedulerTrySleepTimeNanos());
        scheduler.start();
    }

    /**
     * Sets up the runtime to replay a previously recorded schedule.
     *
     * <p>The configured strategy must implement {@link ReplayableSchedulingStrategy}; otherwise a
     * {@link JmcReplayUnsupported} exception is thrown. The recorded trace is loaded via {@link
     * ReplayableSchedulingStrategy#replayRecordedTrace()} before the scheduler is created and
     * started.
     *
     * @param config the configuration (instance of {@link JmcRuntimeConfiguration})
     * @throws JmcCheckerException if the strategy does not support replay
     */
    public static void setupReplay(JmcRuntimeConfiguration config) throws JmcCheckerException {
        LOGGER.debug("Setting up for replay!");
        JmcRuntime.config = config;
        SchedulingStrategy strategy = config.getStrategy();
        if (!(strategy instanceof ReplayableSchedulingStrategy)) {
            LOGGER.error(
                    "The provided strategy is not replayable. Please use a replayable strategy.");
            throw new JmcReplayUnsupported();
        }
        ((ReplayableSchedulingStrategy) strategy).replayRecordedTrace();
        scheduler =
                new Scheduler(
                        strategy,
                        config.getSchedulerTries(),
                        config.getSchedulerTrySleepTimeNanos());
        scheduler.start();
    }

    /**
     * Tears down the runtime by clearing the task manager and shutting down the scheduler.
     *
     * <p>Called once after all iterations are complete.
     *
     * @param report the report passed to the strategy teardown via the scheduler
     */
    public static void tearDown(JmcModelCheckerReport report) {
        LOGGER.debug("Tearing down!");
        taskManager.reset();
        scheduler.shutdown(report);
    }

    /**
     * Reconfigures log4j to write runtime logs to a per-iteration file.
     *
     * <p>The file is named {@code jmc-runtime-<iteration>.log} under the configured report path.
     * Only invoked from {@link #initIteration(int, JmcModelCheckerReport)} when debug logging is
     * enabled.
     *
     * @param iteration the iteration number, used in the log file name
     */
    private static void updateLoggerFile(int iteration) {
        String fileName = config.getReportPath() + "/jmc-runtime-" + iteration + ".log";
        ConfigurationBuilder<BuiltConfiguration> builder =
                ConfigurationBuilderFactory.newConfigurationBuilder();
        Configuration configuration =
                builder.add(
                                builder.newAppender("FILE", "File")
                                        .addAttribute("fileName", fileName)
                                        .addAttribute("append", false)
                                        .add(
                                                builder.newLayout("PatternLayout")
                                                        .addAttribute(
                                                                "pattern",
                                                                "%d [%t] %5p %c{1.} - %m%n")))
                        .add(builder.newRootLogger(Level.DEBUG).add(builder.newAppenderRef("FILE")))
                        .build(false);
        Configurator.reconfigure(configuration);
    }

    /**
     * Initializes the runtime for a given iteration and starts the main task.
     *
     * <p>When debug logging is enabled, redirects logs to a per-iteration file. Initializes the
     * strategy for the iteration, creates the main task (marking it {@link
     * TaskManager.TaskState#BLOCKED}), binds the scheduler to the task manager, emits the {@link
     * JmcRuntimeEvent.Type#START_EVENT} for the main task, and yields control to the scheduler. From
     * the second iteration onward, re-invokes the static initializers of instrumented classes.
     *
     * @param iteration the iteration number
     * @param report the model checker report, forwarded to the strategy via the scheduler
     */
    public static void initIteration(int iteration, JmcModelCheckerReport report) {
        if (config.getDebug()) {
            updateLoggerFile(iteration);
        }
        LOGGER.debug("Initializing iteration");
        scheduler.initIteration(iteration, report);
        Long mainThreadId = taskManager.addNextTask();
        taskManager.markStatus(mainThreadId, TaskManager.TaskState.BLOCKED);

        scheduler.init(taskManager, mainThreadId);
        try {
            scheduler.updateEvent(
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.START_EVENT)
                            .taskId(mainThreadId)
                            .param("startedBy", 1L)
                            .build());
        } catch (HaltTaskException ignored) {
            LOGGER.error("Failed to start main thread.");
        }
        JmcRuntime.yield();
        if (iteration != 0) {
            JmcRuntimeUtils.invokeStaticInitializedClasses(iteration);
        }

    }

    /**
     * Resets the runtime at the end of an iteration.
     *
     * <p>Resets the strategy, clears the task manager, and clears the synchronized-method/-block
     * lock store so the next iteration starts from a clean state.
     *
     * @param iteration the iteration number being reset
     */
    public static void resetIteration(int iteration) {
        scheduler.resetIteration(iteration);
        taskManager.reset();
        JmcRuntimeUtils.clearSyncLocks();
    }

    /**
     * Records the current schedule via the scheduler.
     *
     * <p>Effective only when the configured strategy is replayable; used to persist a buggy
     * schedule for later replay.
     */
    public static void recordTrace() {
        scheduler.recordTrace();
    }

    /**
     * Pauses the current task that invokes this method and yields control to the scheduler. The
     * call returns only when the task that invoked this method is resumed.
     *
     * @param <T> the type of the value delivered when the task is resumed
     * @return the value the scheduler attached when resuming the task (used for reactive events such
     *     as a strategy-provided random value or a symbolic result); may be {@code null}
     */
    public static <T> T yield() {
        Long currentTask = scheduler.currentTask();
        try {
            LOGGER.debug("Yielding task {}", currentTask);
            scheduler.yield();
        } catch (TaskAlreadyPaused e) {
            LOGGER.error("Yielding an already paused task.");
            throw HaltExecutionException.error("Yielding an already paused task.");
        }
        return wait(currentTask);
    }

    /**
     * Pauses the task with the given ID and yields the control to the scheduler. The call returns
     * only when the task with the given ID is resumed.
     *
     * <p>Use with Caution! It is meant to be called when a new concurrent task starts and yields
     * with the new task id. Should not be used otherwise.
     *
     * @param taskId the ID of the task to be paused
     */
    public static void yield(Long taskId) throws HaltTaskException, HaltExecutionException {
        try {
            LOGGER.debug("Yielding task explicitly {}", taskId);
            scheduler.yield(taskId);
        } catch (TaskAlreadyPaused e) {
            LOGGER.error("Yielding an already paused task.");
            throw HaltExecutionException.error("Yielding an already paused task.");
        }
        wait(taskId);
    }

    /**
     * Pauses the task.
     *
     * @param taskId the ID of the task to be paused
     */
    public static void pause(Long taskId) {
        try {
            taskManager.pause(taskId);
        } catch (TaskAlreadyPaused e) {
            LOGGER.error("Current thread is already paused.");
            throw HaltExecutionException.error("Current thread is already paused.");
        }
    }

    /**
     * Blocks until the task with the given ID is resumed and returns the delivered value.
     *
     * <p>Delegates to {@link TaskManager#wait(Long)}. If re-execution of the iteration is requested,
     * a {@link HaltExecutionException#reexecutionNeeded()} is propagated; any other failure is
     * rethrown as a {@link HaltExecutionException} error.
     *
     * @param <T> the type of the value delivered when the task is resumed
     * @param taskId the ID of the task to wait on
     * @return the value delivered when the task is resumed; may be {@code null}
     */
    public static <T> T wait(Long taskId) {
        try {
            return taskManager.wait(taskId);
        } catch (HaltExecutionException e) {
            if (e.isReexecutionNeeded()) {
                LOGGER.debug("Re-execution needed, throwing HaltExecutionException");
                throw HaltExecutionException.reexecutionNeeded();
            } else {
                LOGGER.error("Failed to wait for task: {}", taskId);
                Throwable cause = e.getCause();
                throw HaltExecutionException.error(cause.getMessage());
            }
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.error("Failed to wait for task: {}", taskId);
            Throwable cause = e.getCause();
            throw HaltExecutionException.error(cause.getMessage());
        }
    }

    /**
     * Updates the TaskManager to terminate the task with the given ID and yields control to the
     * scheduler to schedule other tasks.
     *
     * @param taskId the ID of the task to be terminated
     */
    public static void join(Long taskId) {
        LOGGER.debug("Joining task {}", taskId);
        //try {
        taskManager.terminate(taskId);
        //scheduler.yield();
        scheduler.yieldWithoutPausing();
        /*} catch (TaskAlreadyPaused e) {
            LOGGER.error("Joining an already paused task.");
            throw HaltExecutionException.error("Joining an already paused task.");
        }*/
    }

    /**
     * Returns the current task id.
     *
     * @return the current task id
     */
    public static Long currentTask() {
        Long currentTask = scheduler.currentTask();
        if (currentTask == null) {
            LOGGER.error("No current task.");
            throw HaltExecutionException.error("No current task.");
        }
        return currentTask;
    }

    /**
     * Adds a new event to the scheduler which is passed to the strategy.
     *
     * @param event to be added
     */
    public static void updateEvent(JmcRuntimeEvent event) throws HaltTaskException {
        LOGGER.debug("Updating event: {}", event);
        try {
            scheduler.updateEvent(event);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to update event: {}", event);
            taskManager.terminate(event.getTaskId());
            throw e;
        } catch (HaltExecutionException e) {
            if (e.isReexecutionNeeded()) {
                throw HaltExecutionException.reexecutionNeeded();
            }
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to update event: {}", event, e);
            throw HaltExecutionException.error(e.getMessage());
        }
    }

    /**
     * Terminates the task with the given ID.
     *
     * @param taskId the ID of the task to be terminated
     */
    public static void terminate(Long taskId) {
        LOGGER.debug("Terminating task {}", taskId);
        taskManager.terminate(taskId);
    }

    /**
     * Adds a new event to the runtime and yields the control to the scheduler.
     *
     * @param event the new event
     */
    public static <T> T updateEventAndYield(JmcRuntimeEvent event) throws HaltTaskException {
        updateEvent(event);
        return JmcRuntime.yield();
    }

    /**
     * Adds a new task to the runtime and creates a future for that task.
     *
     * @return the ID of the new task
     */
    public static Long addNewTask() {
        Long newTaskId = taskManager.addNextTask();
        LOGGER.debug("Adding new task {}", newTaskId);
        return newTaskId;
    }
}
