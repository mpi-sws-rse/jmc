package org.mpisws.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Runtime environment complete with a scheduler and configuration options used by the model
 * checker.
 *
 * <p>Calls to the runtime are made by the instrumented byte code. These calls are used to record
 * events occurring during the execution of tasks or allow for scheduling changes. For example, the
 * runtime can be used to record Thread creation and deletion.
 *
 * <p>The runtime is a static class that stores minimal states and delegates calls to the {@link
 * Scheduler} which retains all the state.
 */
public class JmcRuntime {

    private static Logger LOGGER = LogManager.getLogger(JmcRuntime.class);

    private static final TaskManager taskManager = new TaskManager();

    private static Scheduler scheduler;

    private static JmcRuntimeConfiguration config;

    /**
     * Sets up the runtime with the given configuration.
     *
     * @param config the configuration (instance of {@link JmcRuntimeConfiguration})
     */
    public static void setup(JmcRuntimeConfiguration config) {
        LOGGER.debug("Setting up!");
        JmcRuntime.config = config;
        scheduler = new Scheduler(config.getStrategy());
        scheduler.start();
    }

    /** Tears down the runtime by shutting down the scheduler adn clearing the task manager. */
    public static void tearDown() {
        LOGGER.debug("Tearing down!");
        taskManager.reset();
        scheduler.shutdown();
    }

    /**
     * Initializes the runtime with the main thread for a given iteration.
     *
     * <p>Initializes the scheduler with the main thread and marks it as ready.
     *
     * @param iteration the iteration number
     */
    public static void initIteration(int iteration) {
        LOGGER = LogManager.getLogger(JmcRuntime.class.getName() + " Iteration=" + iteration);
        LOGGER.info("Initializing iteration");
        Long mainThreadId = taskManager.addNextTask();
        taskManager.markStatus(mainThreadId, TaskManager.TaskState.BLOCKED);

        scheduler.init(taskManager, mainThreadId);
        try {
            scheduler.updateEvent(
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.START_EVENT)
                            .taskId(mainThreadId)
                            .build());
        } catch (HaltTaskException ignored) {
            LOGGER.error("Failed to start main thread.");
        }
        JmcRuntime.yield();
    }

    /** Resets the runtime for a new iteration. */
    public static void resetIteration() {
        scheduler.endIteration();
        taskManager.reset();
    }

    /**
     * Pauses the current task that invokes this method and yields the control to the scheduler. The
     * call returns only when the task that invoked this method is resumed.
     */
    public static void yield() {
        Long currentTask = scheduler.currentTask();
        try {
            LOGGER.debug("Yielding task {}", currentTask);
            scheduler.yield();
        } catch (TaskAlreadyPaused e) {
            LOGGER.error("Yielding an already paused task.");
            System.exit(1);
        }
        taskManager.wait(currentTask);
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
    public static void yield(Long taskId) {
        try {
            LOGGER.debug("Yielding task {}", taskId);
            scheduler.yield(taskId);
        } catch (TaskAlreadyPaused e) {
            LOGGER.error("Yielding an already paused task.");
            System.exit(1);
        }
        taskManager.wait(taskId);
    }

    /**
     * Called by the task that spawns a new task to just pause and wait. Everything else is handled
     * in the {@link JmcRuntime#addNewTask()} method.
     */
    public static void spawn() {
        LOGGER.debug("Spawning new task and waiting");
        Long currentTask = scheduler.currentTask();
        try {
            taskManager.pause(currentTask);
        } catch (TaskAlreadyPaused e) {
            LOGGER.error("Current thread is already paused.");
            System.exit(1);
        }
        taskManager.wait(currentTask);
    }

    /**
     * Updates the TaskManager to terminate the task with the given ID and yields control to the
     * scheduler to schedule other tasks.
     *
     * @param taskId the ID of the task to be terminated
     */
    public static void join(Long taskId) {
        LOGGER.debug("Joining task {}", taskId);
        try {
            scheduler.yield();
        } catch (TaskAlreadyPaused e) {
            LOGGER.error("Joining an already paused task.");
            System.exit(1);
        }
        taskManager.terminate(taskId);
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
            System.exit(1);
        }
        return currentTask;
    }

    /**
     * Adds a new event to the scheduler which is passed to the strategy.
     *
     * @param event to be added
     */
    public static void updateEvent(RuntimeEvent event) throws HaltTaskException {
        LOGGER.debug("Updating event: {}", event);
        try {
            scheduler.updateEvent(event);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to update event: {}", event);
            taskManager.terminate(event.getTaskId());
            throw e;
        }
    }

    /**
     * Adds a new event to the runtime and yields the control to the scheduler.
     *
     * @param event the new event
     */
    public static void updateEventAndYield(RuntimeEvent event) throws HaltTaskException {
        updateEvent(event);
        JmcRuntime.yield();
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
