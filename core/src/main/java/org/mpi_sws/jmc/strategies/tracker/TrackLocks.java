package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the locks acquired and released events of tasks. */
public class TrackLocks implements Tracker {

    private static final Logger LOGGER = LogManager.getLogger(TrackLocks.class);

    /**
     * For each lock, Contains a list of tasks that want the lock. Once the task acquires the lock,
     * it is removed from the set.
     */
    private final Map<Object, Set<Long>> waitingTasks;

    /**
     * For each lock, the set of tasks that have requested it while it was free but have not yet been
     * confirmed as the owner (via a {@code LOCK_ACQUIRED_EVENT}).
     */
    private final Map<Object, Set<Long>> wantingTasks;

    /** Maps each known task to the lock it currently holds, if any. */
    private final Map<Long, Optional<Object>> activeTasks;

    /** Constructs a new TrackLocks object. */
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
     * <p>If it is not yet acquired, it is put in {@link TrackLocks#wantingTasks} and retained in
     * active tasks.
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
            if (tryLock(taskId, lock)) {
                return getActiveTasks();
            }
        } else if (type == JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT) {
            Object lock = event.getParam("instance");
            lockAcquired(taskId, lock);
        } else if (type == JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT) {
            Object lock = event.getParam("instance");
            unlock(taskId, lock);
        }
        return getActiveTasks();
    }

    /**
     * Handles a lock-acquire request for a task. Three cases: the task already holds the lock
     * (reentrant, allowed); the lock is held by another task (the task is blocked and removed from
     * active); or the lock is free (the task is recorded as wanting it and stays active).
     *
     * @param taskId the requesting task
     * @param lock the lock instance being requested
     * @return {@code true} if the task may proceed (reentrant case), {@code false} otherwise
     */
    protected boolean tryLock(Long taskId, Object lock) {
        // Want the lock. Three cases.
        // 1. Current task already has the lock. Ignore.
        Optional<Object> owner = activeTasks.get(taskId);
        if (owner != null && owner.isPresent()) {
            if (owner.get() == lock) {
                LOGGER.debug("Reentrant lock already included by task {}", taskId);
                return true;
            }
        }
        // 2. The lock is already acquired by another task. The current task is added to the
        // waiting list.
        if (waitingTasks.containsKey(lock)) {
            LOGGER.debug("Task {} waits for lock {}", taskId, lock.hashCode());
            Set<Long> tasks = waitingTasks.get(lock);
            tasks.add(taskId);
            activeTasks.remove(taskId);
        } else {
            // 3. The lock is not acquired by any task. The current task is added to the
            // wanting
            // list.
            LOGGER.debug("Task {} wants lock {}", taskId, lock.hashCode());
            wantingTasks.putIfAbsent(lock, new HashSet<>());
            wantingTasks.get(lock).add(taskId);
        }
        return false;
    }

    /**
     * Records that a task has acquired a lock. The task becomes the owner; every other task that was
     * wanting the same lock is moved to the waiting set (blocked) and removed from active.
     *
     * @param taskId the task that acquired the lock
     * @param lock the lock instance that was acquired
     */
    protected void lockAcquired(Long taskId, Object lock) {
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
    }

    /**
     * Records that a task has released a lock. The owner is cleared and every task waiting on the
     * lock is made active again (re-contending as wanting).
     *
     * @param taskId the task releasing the lock
     * @param lock the lock instance being released
     */
    protected void unlock(Long taskId, Object lock) {
        // The lock is released. The waiting tasks are marked as active.
        LOGGER.debug("Task {} released lock {}", taskId, lock.hashCode());
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

    /**
     * Returns the set of tasks not currently blocked on a lock (the keys of {@link #activeTasks}).
     *
     * @return the set of active (non-lock-blocked) tasks
     */
    protected Set<Long> getActiveTasks() {
        return activeTasks.keySet();
    }

    /** Clears all tracked lock state. */
    @Override
    public void reset() {
        activeTasks.clear();
        waitingTasks.clear();
        wantingTasks.clear();
    }
}
