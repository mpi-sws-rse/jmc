package org.mpisws.jmc.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Encapsulates all the operations related to Task objects used by the runtime Except the
 * SchedulerTask The encapsulation ensures no memory leak when creating many tasks.
 */
public class TaskManager {

    private static final Logger LOGGER = LogManager.getLogger(TaskManager.class);

    /**
     * The state of each task managed by the @RuntimeEnvironment is represented by one of the
     * following.
     */
    public enum TaskState {
        RUNNING,
        BLOCKED,
        CREATED,
        TERMINATED,
    }

    /** Stores a set of custom IDs used by the Runtime. */
    private Long idCounter;

    private final Object idCounterLock = new Object();

    /** Stores the state of each task. */
    private final Map<Long, TaskState> taskStates;

    /** Stores the future of blocked tasks. */
    private final Map<Long, CompletableFuture<?>> taskFutures;

    private final Object tasksLock = new Object();

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Returns the next task ID to be assigned.
     *
     * @return the next task ID to be assigned
     */
    private Long nextTaskId() {
        synchronized (idCounterLock) {
            return idCounter++;
        }
    }

    /** Constructs a new TaskManager object. */
    public TaskManager() {
        this.idCounter = 1L;
        this.taskStates = new HashMap<>();
        this.taskFutures = new HashMap<>();
    }

    /** Resets the TaskManager object. */
    public void reset() {
        synchronized (idCounterLock) {
            idCounter = 1L;
        }
        synchronized (tasksLock) {
            taskStates.clear();
            for (CompletableFuture<?> future : taskFutures.values()) {
                future.complete(null);
            }
            taskFutures.clear();
        }
    }

    /**
     * Adds a new task to the TaskManager object. The task is assigned the next available task ID
     * and a default name "Task-ID".
     *
     * @return the ID of the task
     */
    public Long addNextTask() {
        Long customTaskId = nextTaskId();
        synchronized (tasksLock) {
            taskStates.put(customTaskId, TaskState.CREATED);
        }
        return customTaskId;
    }

    /**
     * Pauses the task with the specified custom ID. A new future is created and stored in the
     * {@link TaskManager#taskFutures} map. If the task is already paused, a {@link
     * TaskAlreadyPaused} exception is thrown.
     *
     * @param taskId the custom ID of the task
     * @return a future that completes when the task is resumed
     * @throws TaskAlreadyPaused if the task with the specified custom ID is already paused
     */
    public <T> CompletableFuture<T> pause(Long taskId) throws TaskAlreadyPaused {
        CompletableFuture<T> future = new CompletableFuture<>();
        synchronized (tasksLock) {
            if (taskFutures.containsKey(taskId)) {
                throw new TaskAlreadyPaused();
            }
            taskStates.put(taskId, TaskState.BLOCKED);
            taskFutures.put(taskId, future);
        }
        return future;
    }

    /**
     * Resumes the task with the specified custom ID. The future associated with the task is
     * completed.
     *
     * @param taskId the custom ID of the task
     * @throws TaskNotExists if the task with the specified custom ID does not exist
     */
    public void resume(Long taskId) throws TaskNotExists {
        synchronized (tasksLock) {
            CompletableFuture<?> future = taskFutures.get(taskId);
            if (future == null) {
                // The task is not paused or has been completed.
                throw new TaskNotExists(taskId);
            }
            future.complete(null);
            taskFutures.remove(taskId);
            taskStates.put(taskId, TaskState.RUNNING);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void resume(Long taskId, T value) throws TaskNotExists {
        CompletableFuture<?> future;
        synchronized (tasksLock) {
            future = taskFutures.get(taskId);
            if (future == null) {
                // The task is not paused or has been completed.
                throw new TaskNotExists(taskId);
            }
            taskFutures.remove(taskId);
            taskStates.put(taskId, TaskState.RUNNING);
        }
        try {
            CompletableFuture<T> castedFuture = (CompletableFuture<T>) future;
            castedFuture.complete(value);
        } catch (ClassCastException e) {
            LOGGER.error("Failed to cast future for task: {}", taskId);
            throw new TaskNotExists(taskId);
        }
    }

    public void error(Long taskId, Exception e) {
        synchronized (tasksLock) {
            CompletableFuture<?> future = taskFutures.get(taskId);
            if (future == null) {
                return;
            }
            future.completeExceptionally(e);
            taskFutures.remove(taskId);
        }
    }

    /**
     * Terminates the task with the specified custom ID. The future associated with the task is
     * completed.
     *
     * @param taskId the custom ID of the task
     */
    public void terminate(Long taskId) {
        synchronized (tasksLock) {
            taskStates.put(taskId, TaskState.TERMINATED);
            CompletableFuture<?> future = taskFutures.get(taskId);
            if (future == null) {
                return;
            }
            future.complete(null);
            taskFutures.remove(taskId);
        }
    }

    /**
     * Return the size of the task pool.
     *
     * @return the size of the task pool
     */
    public int size() {
        synchronized (tasksLock) {
            return taskStates.size();
        }
    }

    /**
     * Update the state of the task with the specified custom ID.
     *
     * @param taskId the custom ID of the task
     * @param state the new state of the task
     */
    public void markStatus(Long taskId, TaskState state) {
        synchronized (tasksLock) {
            taskStates.put(taskId, state);
        }
    }

    /**
     * Return the state of the task with the specified custom ID.
     *
     * @param taskId the custom ID of the task
     * @return the state of the task
     */
    public TaskState getStatus(Long taskId) {
        synchronized (tasksLock) {
            return taskStates.get(taskId);
        }
    }

    /**
     * Return all the tasks with the specified state.
     *
     * @param state the state of the tasks to find
     * @return a list of tasks with the specified state
     */
    public List<Long> findTasksWithStatus(TaskState state) {
        List<Long> result = new ArrayList<>();
        synchronized (tasksLock) {
            for (Map.Entry<Long, TaskState> entry : taskStates.entrySet()) {
                if (entry.getValue() == state) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    /**
     * Return custom IDs of all the tasks.
     *
     * @return a list of custom IDs of all the tasks
     */
    public List<Long> getActiveTasks() {
        ArrayList<Long> result = new ArrayList<>();
        synchronized (tasksLock) {
            for (Map.Entry<Long, TaskState> taskStateEntry : taskStates.entrySet()) {
                if (taskStateEntry.getValue() != TaskState.TERMINATED) {
                    result.add(taskStateEntry.getKey());
                }
            }
        }
        return result;
    }

    /**
     * Return true if the task with the specified system task ID is in the task pool and with status
     * provided.
     *
     * @param taskId the custom ID of the task
     * @param state the state of the task
     * @return true if the task exists with status
     */
    public boolean isTaskOfStatus(Long taskId, TaskState state) {
        synchronized (tasksLock) {
            if (!taskStates.containsKey(taskId)) {
                return false;
            }
            return taskStates.get(taskId) == state;
        }
    }

    /**
     * Wait for the task with the specified custom ID to complete.
     *
     * @param taskId the custom ID of the task
     */
    @SuppressWarnings("unchecked")
    public <T> T wait(Long taskId) throws InterruptedException, ExecutionException {
        CompletableFuture<?> future;
        synchronized (tasksLock) {
            future = taskFutures.get(taskId);
        }
        if (future == null) {
            return null;
        }
        try {
            CompletableFuture<T> castedFuture = (CompletableFuture<T>) future;
            return castedFuture.get();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof HaltTaskException) {
                throw (HaltTaskException) cause;
            } else {
                LOGGER.error("Error waiting for task: {}, {}", taskId, e.getMessage());
                throw e;
            }
        }
    }

    /** Stop all the tasks in the task pool. */
    public void stopAll() {
        synchronized (tasksLock) {
            for (Map.Entry<Long, CompletableFuture<?>> entry : taskFutures.entrySet()) {
                entry.getValue()
                        .completeExceptionally(HaltExecutionException.error("Stopping execution"));
            }
            taskFutures.clear();
            taskStates.clear();
        }
    }
}
