package org.mpi_sws.jmc.strategies.tracker;

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the locks acquired and released events of tasks.
 */
public class TrackLocks implements Tracker {

    /**
     * For each lock, Contains a list of tasks that want the lock. Once the task acquires the
     * lock, it is removed from the set.
     */
    private final Map<Object, Set<Long>> waitingTasks;

    private final Map<Object, Set<Long>> wantingTasks;

    private final Map<Long, Optional<Object>> activeTasks;

    /**
     * Constructs a new TrackLocks object.
     */
    public TrackLocks() {
        this.waitingTasks = new ConcurrentHashMap<>();
        this.wantingTasks = new ConcurrentHashMap<>();
        this.activeTasks = new ConcurrentHashMap<>();
    }

    /**
     * Updates based on lock acquire and release events.
     *
     * <p>For every acquire event, if the lock is already acquired, the task is made to wait.
     * Tracked in {@link TrackLocks#waitingTasks}.
     *
     * <p>If it is not yet acquired, it is put in {@link TrackLocks#wantingTasks} and retained
     * in active tasks.
     *
     * <p>For every release event, the corresponding waiting tasks are marked as active.
     *
     * @param event the event to update
     * @return the set of active tasks
     */
    @Override
    public Set<Long> updateEvent(JmcRuntimeEvent event) {

        Long taskId = event.getTaskId();
        if (taskId == null) {
            // Ignore events without a task ID
            return getActiveTasks();
        }
        activeTasks.putIfAbsent(taskId, Optional.empty());

        JmcRuntimeEvent.Type type = event.getType();

        if (type == JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT) {
            Object lock = event.getParam("instance");
            // Want the lock. Three cases.
            // 1. Current task already has the lock. Ignore.
            Optional<Object> owner = activeTasks.get(taskId);
            if (owner != null && owner.isPresent()) {
                if (owner.get() == lock) {
                    TrackActiveTasksStrategy.LOGGER.debug("Reentrant lock already included by task {}", taskId);
                    return getActiveTasks();
                }
            }
            // 2. The lock is already acquired by another task. The current task is added to the
            // waiting list.
            if (waitingTasks.containsKey(lock)) {
                TrackActiveTasksStrategy.LOGGER.debug("Task {} waits for lock {}", taskId, lock.hashCode());
                Set<Long> tasks = waitingTasks.get(lock);
                tasks.add(taskId);
                activeTasks.remove(taskId);
            } else {
                // 3. The lock is not acquired by any task. The current task is added to the
                // wanting
                // list.
                TrackActiveTasksStrategy.LOGGER.debug("Task {} wants lock {}", taskId, lock.hashCode());
                wantingTasks.putIfAbsent(lock, new HashSet<>());
                wantingTasks.get(lock).add(taskId);
            }
        } else if (type == JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT) {
            Object lock = event.getParam("instance");
            // The lock is acquired by the current task. Remove it from the wanting list and add
            // the rest to waiting
            // list.
            activeTasks.put(taskId, Optional.of(lock));
            waitingTasks.putIfAbsent(lock, new HashSet<>());
            Set<Long> wantingList = wantingTasks.get(lock);
            if (wantingList != null) {
                for (Long wantingTask : wantingList) {
                    // If the task is not already in the waiting list, add it to the waiting
                    // list
                    if (Objects.equals(wantingTask, taskId)) {
                        // Ignore the current task
                        continue;
                    }
                    waitingTasks.get(lock).add(wantingTask);
                    activeTasks.remove(wantingTask);
                }
                wantingTasks.remove(lock);
            }
        } else if (type == JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT) {
            Object lock = event.getParam("instance");
            // The lock is released. The waiting tasks are marked as active.
            TrackActiveTasksStrategy.LOGGER.debug("Task {} released lock {}", taskId, lock.hashCode());
            activeTasks.put(taskId, Optional.empty());
            Set<Long> blockedTasks = waitingTasks.get(lock);
            wantingTasks.put(lock, new HashSet<>());
            if (blockedTasks != null) {
                for (Long blockedTask : blockedTasks) {
                    activeTasks.put(blockedTask, Optional.empty());
                    wantingTasks.get(lock).add(blockedTask);
                }
                waitingTasks.remove(lock);
            }
        }
        return getActiveTasks();
    }

    private Set<Long> getActiveTasks() {
        return activeTasks.keySet();
    }

    @Override
    public void reset() {
        activeTasks.clear();
        waitingTasks.clear();
        wantingTasks.clear();
    }
}
