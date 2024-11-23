package org.mpisws.strategies;

import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A strategy that tracks the tasks that are waiting for a lock. Helps avoid deadlocks */
public abstract class TrackLockWaitingStrategy extends TrackActiveTasksStrategy {

    private final Map<Object, Set<Long>> waitingTasks;
    private final Map<Long, Integer> locksPerTasks;

    /** Constructs a new TrackLockWaitingStrategy object. */
    public TrackLockWaitingStrategy() {
        this.waitingTasks = new ConcurrentHashMap<>();
        this.locksPerTasks = new ConcurrentHashMap<>();
    }

    /**
     * Tracks the lock acquire and release events.
     *
     * <p>For acquire events, the task is marked as inactive and the task is added to the waiting
     * tasks for that lock. Additionally, the number of locks held by the task is incremented.
     *
     * <p>For release events, all tasks are release for that lock and the number of locks held by
     * these tasks is decremented.
     */
    @Override
    public void updateEvent(RuntimeEvent event) {
        super.updateEvent(event);
        if (event.getType() == RuntimeEventType.LOCK_ACQUIRE_EVENT) {
            Object lock = event.getParam("lock");
            Long taskId = event.getTaskId();
            Set<Long> tasks = waitingTasks.get(lock);
            if (tasks == null) {
                tasks = new HashSet<>();
                tasks.add(taskId);
                waitingTasks.put(lock, tasks);
            }
            tasks.add(taskId);
            if (locksPerTasks.containsKey(taskId)) {
                locksPerTasks.put(taskId, locksPerTasks.get(taskId) + 1);
            } else {
                locksPerTasks.put(taskId, 1);
            }
            markInactive(taskId);
        } else if (event.getType() == RuntimeEventType.LOCK_RELEASE_EVENT) {
            Object lock = event.getParam("lock");
            Set<Long> blockedTasks = waitingTasks.remove(lock);
            if (blockedTasks != null) {
                for (Long blockedTask : blockedTasks) {
                    Integer numLocks =
                            locksPerTasks.put(blockedTask, locksPerTasks.get(blockedTask) - 1);
                    if (numLocks != null && numLocks == 1) {
                        markActive(blockedTask);
                    }
                }
            }
        }
    }
}
