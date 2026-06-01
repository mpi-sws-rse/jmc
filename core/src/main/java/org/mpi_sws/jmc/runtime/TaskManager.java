package org.mpi_sws.jmc.runtime;

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
 * Owns all per-task state used by the runtime (except the scheduler thread itself).
 *
 * <p>A <em>task</em> is any concurrent computation managed by JMC (a thread, a future, an executor
 * task, ...). For each task this class tracks its {@link TaskState} and, while the task is paused,
 * the {@link CompletableFuture} it is blocked on. Resuming a task amounts to completing that future,
 * optionally with a value.
 *
 * <p>Centralizing task ownership here ensures futures are completed and dropped consistently,
 * avoiding memory leaks when many tasks are created. Used by {@link JmcRuntime} and by the {@code
 * Scheduler} (via the runtime) to pause, resume, block, and terminate tasks.
 */
public class TaskManager {

    /** Logger used to trace task resume failures and the "stop all" path. */
    private static final Logger LOGGER = LogManager.getLogger(TaskManager.class);

    /**
     * The lifecycle state of a task managed by the {@link TaskManager}.
     */
    public enum TaskState {
        /** The task is currently executing (it has been resumed by the scheduler). */
        RUNNING,
        /** The task is paused, waiting on its future to be completed. */
        BLOCKED,
        /** The task has been allocated an ID but has not started running yet. */
        CREATED,
        /** The task has finished (or been terminated) and will not run again. */
        TERMINATED,
    }

    /**
     * Monotonic counter for the next task ID to assign. Starts at 1 (the main task) and is handed
     * out by {@link #nextTaskId()}. Guarded by {@link #idCounterLock}.
     */
    private Long idCounter;

    /** Lock guarding {@link #idCounter}. */
    private final Object idCounterLock = new Object();

    /**
     * Maps each task ID to its current {@link TaskState}. Guarded by {@link #tasksLock}.
     */
    private final Map<Long, TaskState> taskStates;

    /**
     * Maps each currently-paused task ID to the {@link CompletableFuture} it is blocked on.
     * Completing the future resumes the task. Guarded by {@link #tasksLock}.
     */
    private final Map<Long, CompletableFuture<?>> taskFutures;

    /** Lock guarding {@link #taskStates} and {@link #taskFutures}. */
    private final Object tasksLock = new Object();

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

    /**
     * Constructs a new TaskManager object.
     */
    public TaskManager() {
        this.idCounter = 1L;
        this.taskStates = new HashMap<>();
        this.taskFutures = new HashMap<>();
    }

    /**
     * Resets the TaskManager object.
     */
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

    /**
     * Resumes the task with the specified custom ID, delivering a value to it.
     *
     * <p>The task's future is removed and the task is marked {@link TaskState#RUNNING}, then the
     * future is completed with {@code value}. The value becomes the return of the task's pending
     * {@link #wait(Long)} call (used to deliver reactive/symbolic results). If the stored future
     * cannot be cast to the expected type, a {@link TaskNotExists} is thrown.
     *
     * @param <T> the type of the value delivered to the task
     * @param taskId the custom ID of the task
     * @param value the value to deliver to the resumed task
     * @throws TaskNotExists if the task with the specified custom ID does not exist
     */
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

    /**
     * Completes the task's future exceptionally with the given exception.
     *
     * <p>Used to unblock a paused task by signalling a failure to its pending {@link #wait(Long)}
     * call (for example, blocking a task with a {@link HaltTaskException}). Does nothing if the task
     * has no pending future.
     *
     * @param taskId the custom ID of the task
     * @param e the exception to complete the task's future with
     */
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
     * @param state  the new state of the task
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
     * @param state  the state of the task
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
     * Blocks until the task with the specified custom ID is resumed, returning any delivered value.
     *
     * <p>Blocks on the task's future via {@code get()}. Returns {@code null} immediately if the task
     * has no pending future. A {@link HaltTaskException} or a re-execution {@link
     * HaltExecutionException} wrapped as the future's cause is unwrapped and rethrown; any other
     * failure is propagated as {@link ExecutionException}. Invoked by {@link JmcRuntime#wait(Long)}.
     *
     * @param <T> the type of the value delivered when the task is resumed
     * @param taskId the custom ID of the task
     * @return the value delivered when the task is resumed, or {@code null} if there is no pending
     *     future
     * @throws InterruptedException if the waiting thread is interrupted
     * @throws ExecutionException if the task's future completed exceptionally
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
            } else if (cause instanceof HaltExecutionException && ((HaltExecutionException) cause).isReexecutionNeeded()) {
                throw HaltExecutionException.reexecutionNeeded();
            } else {
                throw e;
            }
        }
    }

    /**
     * Stops every task in the pool at once.
     *
     * <p>Completes all pending task futures exceptionally with a {@link HaltExecutionException}
     * error and then clears both the futures and the state map. This is the bulk counterpart to the
     * incremental {@link #doNextStop()} / {@link #stopTask(Long)} teardown.
     */
    public void stopAll() {
        synchronized (tasksLock) {
            for (Map.Entry<Long, CompletableFuture<?>> entry : taskFutures.entrySet()) {
                entry.getValue()
                        .completeExceptionally(HaltExecutionException.error("Stopping execution"));
                // wait
            }
            taskFutures.clear();
            taskStates.clear();
        }
    }

    /**
     * Selects the next task to stop while the scheduler is unwinding an execution.
     *
     * <p>Returns the highest task ID that is neither {@link TaskState#TERMINATED} nor {@link
     * TaskState#CREATED} (i.e. a started task that can still be stopped), or {@code -1} if none
     * remain. Used by the scheduler's "stop all" mode to tear tasks down in descending ID order.
     *
     * @return the ID of the next task to stop, or {@code -1L} if there is none
     */
    public Long doNextStop() {
        synchronized (tasksLock) {
            List<Long> taskIds = new ArrayList<>(taskStates.keySet());
            taskIds.sort(Long::compareTo);
            for (int i = taskIds.size() - 1; i >= 0; i--) {
                Long taskId = taskIds.get(i);
                if (taskStates.get(taskId) != TaskState.TERMINATED &&
                        taskStates.get(taskId) != TaskState.CREATED) {
                    return taskId;
                }
            }
        }
        return -1L;
    }

    /**
     * Stops a single paused task by failing its future with a re-execution signal.
     *
     * <p>Completes the task's future exceptionally with {@link
     * HaltExecutionException#reexecutionNeeded()}, causing its pending {@link #wait(Long)} to
     * unwind. Does nothing if the task has no pending future. Invoked by the scheduler's "stop all"
     * mode (after {@link #doNextStop()} selects the task).
     *
     * @param taskId the custom ID of the task to stop
     */
    public void stopTask(Long taskId) {
        synchronized (tasksLock) {
            CompletableFuture<?> future = taskFutures.get(taskId);
            if (future != null) {
                future.completeExceptionally(HaltExecutionException.reexecutionNeeded());
            }
        }
    }
}
