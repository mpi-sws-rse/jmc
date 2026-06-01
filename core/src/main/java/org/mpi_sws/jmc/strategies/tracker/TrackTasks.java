package org.mpi_sws.jmc.strategies.tracker;

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the tasks start finish and join request events.
 */
public class TrackTasks implements Tracker {
    /** Tasks that have started and not yet finished or blocked on a join. */
    private final Set<Long> activeTasks;
    /** For each target task, the set of tasks blocked joining on it. */
    private final Map<Long, Set<Long>> waitingTasks;
    /** Tasks that have finished. */
    private final Set<Long> completedTasks;
    /** Lock guarding {@link #activeTasks}, {@link #waitingTasks}, and {@link #completedTasks}. */
    private final Object tasksLock = new Object();

    /**
     * Constructs a new TrackTasks object.
     */
    public TrackTasks() {
        this.activeTasks = new HashSet<>();
        this.completedTasks = new HashSet<>();
        this.waitingTasks = new ConcurrentHashMap<>();
    }

    /**
     * Tracks task start, finish, and join events.
     *
     * <p>On {@link JmcRuntimeEvent.Type#START_EVENT} the task becomes active. On {@link
     * JmcRuntimeEvent.Type#FINISH_EVENT} the task is moved to completed and any tasks joining on it
     * are released back to active. On {@link JmcRuntimeEvent.Type#JOIN_REQUEST_EVENT}, if the target
     * task (param {@code waitingTask}) has not yet completed, the requesting task is blocked until it
     * does.
     *
     * @param event the event to process
     * @return the set of currently active tasks
     */
    @Override
    public Set<Long> updateEvent(JmcRuntimeEvent event) {
        if (event.getType() == JmcRuntimeEvent.Type.START_EVENT) {
            synchronized (tasksLock) {
                activeTasks.add(event.getTaskId());
            }
        } else if (event.getType() == JmcRuntimeEvent.Type.FINISH_EVENT) {
            Long eventTask = event.getTaskId();
            synchronized (tasksLock) {
                activeTasks.remove(eventTask);
                completedTasks.add(eventTask);
                Set<Long> waitingList = waitingTasks.get(eventTask);
                if (waitingList != null) {
                    activeTasks.addAll(waitingList);
                    waitingTasks.remove(eventTask);
                }
            }
        } else if (event.getType() == JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT) {
            Long requestingTask = event.getTaskId();
            Long requestedTask = event.getParam("waitingTask");

            synchronized (tasksLock) {
                // If the requested task is active or not completed, mark the requesting task as
                // waiting
                if (activeTasks.contains(requestedTask)
                        || !completedTasks.contains(requestedTask)) {
                    Set<Long> waitingList =
                            waitingTasks.computeIfAbsent(requestedTask, k -> new HashSet<>());
                    waitingList.add(requestingTask);
                    activeTasks.remove(requestingTask);
                }
                // else nothing to do. The task to wait on is already completed and hence the
                // scheduler can continue picking the current task
            }
        }
        return getActiveTasks();
    }

    /**
     * Returns a snapshot copy of the currently active tasks.
     *
     * @return a copy of the active task set
     */
    private Set<Long> getActiveTasks() {
        synchronized (tasksLock) {
            return new HashSet<>(activeTasks);
        }
    }

    /** Clears all tracked task state. */
    @Override
    public void reset() {
        synchronized (tasksLock) {
            activeTasks.clear();
            waitingTasks.clear();
            completedTasks.clear();
        }
    }
}
