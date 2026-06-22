package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.ValueTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the locks acquired and released events of tasks. */
public class TrackLocks implements Tracker {

    private static final Logger LOGGER = LogManager.getLogger(TrackLocks.class);

    /**
     * For each lock, Contains a list of tasks that want the lock. Once the task acquires the lock,
     * it is removed from the set.
     */
    private final Map<Object, Set<LockRequest>> waitingTasks;

    /**
     * For each lock, the set of tasks that have requested it while it was free but have not yet been
     * confirmed as the owner (via a {@code LOCK_ACQUIRED_EVENT}).
     */
    private final Map<Object, Set<LockRequest>> wantingTasks;

    /** Maps each known task to the lock it currently holds, if any. */
    private final Map<Long, Optional<Object>> activeTasks;


    private final ValueTracker externalValueTracker;

    /** Constructs a new TrackLocks object. */
    public TrackLocks() {
        this.waitingTasks = new ConcurrentHashMap<>();
        this.wantingTasks = new ConcurrentHashMap<>();
        this.activeTasks = new ConcurrentHashMap<>();
        this.externalValueTracker = new ValueTracker();
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

        LockRequest lockRequest = isLockAcquire(event);

        if (lockRequest != null) {
            Object lock = event.getParam("instance");
            boolean result = tryLock(lockRequest, lock);
            if (result) {
                return getActiveTasks();
            }
        } else if (event.isLockAcquired()) {
            Object lock = event.getParam("instance");
            lockAcquired(taskId, lock);
        } else if (event.isLockRelease()) {
            Object lock = event.getParam("instance");
            unlock(taskId, lock);
        } else if (event.isLocked()) {
            Object lock = event.getParam("instance");
            boolean result = waitingTasks.containsKey(lock);
            storeExternalValue(taskId, result);
        } else if (event.isLockOwner()) {
            Object lock = event.getParam("instance");
            storeExternalValue(taskId, isLockOwned(lockRequest, lock));
        }
        return getActiveTasks();
    }

    private LockRequest isLockAcquire(JmcRuntimeEvent  event) {
        if (event.getType() == JmcRuntimeEvent.Type.FAIR_BLOCKING_LOCK_ACQUIRE_EVENT) {
            return LockRequest.fairBlockingRequest(event.getTaskId());
        }

        if (event.getType() == JmcRuntimeEvent.Type.FAIR_NONBLOCKING_LOCK_ACQUIRE_EVENT) {
            return LockRequest.fairNonblockingRequest(event.getTaskId());
        }

        if (event.getType() == JmcRuntimeEvent.Type.UNFAIR_BLOCKING_LOCK_ACQUIRE_EVENT) {
            return LockRequest.unfairBlockingRequest(event.getTaskId());
        }

        if (event.getType() == JmcRuntimeEvent.Type.UNFAIR_NONBLOCKING_LOCK_ACQUIRE_EVENT) {
            return LockRequest.unfairNonblockingRequest(event.getTaskId());
        }

        return null;
    }

    /**
     * Handles a lock-acquire request for a task. Two cases: the request is blocking (the task may be blocked if the
     * lock is held by another task) or non-blocking (the task is not blocked and may proceed if the lock is free).
     *
     * @param lockRequest the lock request stats including the requester thread-id and type of the request
     * @param lock the lock instance being requested
     * @return {@code true} if the task may proceed (reentrant case), {@code false} otherwise
     */
    protected boolean tryLock(LockRequest lockRequest, Object lock) {
        if (lockRequest.isBlocking()) {
            return blockingTryLock(lockRequest, lock);
        } else {
            return nonblockingTryLock(lockRequest, lock);
        }
    }

    /**
     * Handles a blocking lock-acquire request for a task. Three cases: the task already holds the lock
     * (reentrant, allowed); the lock is held by another task (the task is blocked and removed from
     * active); or the lock is free (the task is recorded as wanting it and stays active).
     *
     * @param lockRequest the lock request stats including the requester thread-id and type of the request
     * @param lock the lock instance being requested
     * @return {@code true} if the task may proceed (reentrant case), {@code false} otherwise
     */
    private boolean blockingTryLock(LockRequest lockRequest, Object lock) {
        // Want the lock. Three cases.
        // 1. Current task already has the lock. Ignore.
        if (isLockOwned(lockRequest, lock)) {
            return true;
        }
        // 2. The lock is already acquired by another task. The current task is added to the
        // waiting list.
        if (waitingTasks.containsKey(lock)) {
            LOGGER.debug("Task {} waits for lock {}", lockRequest.getTaskId(), lock.hashCode());
            Set<LockRequest> lockRequests = waitingTasks.get(lock);
            lockRequests.add(lockRequest);
            activeTasks.remove(lockRequest.getTaskId());
        } else {
            // 3. The lock is not acquired by any task. The current task is added to the
            // wanting list.
            LOGGER.debug("Task {} wants lock {}", lockRequest.getTaskId(), lock.hashCode());
            wantingTasks.putIfAbsent(lock, new HashSet<>());
            wantingTasks.get(lock).add(lockRequest);
        }
        return false;
    }

    private boolean nonblockingTryLock(LockRequest lockRequest, Object lock) {
        // Want the lock. Three cases.
        // 1. Current task already has the lock. Ignore.
        if (isLockOwned(lockRequest, lock)) {
            storeExternalValue(lockRequest.getTaskId(), true);
            return true;
        }

        // 2. The lock is already acquired by another task. The current task failed to get the lock
        // and will continue without it.
        if (waitingTasks.containsKey(lock)) {
            LOGGER.debug("Task {} failed to acquire lock {}", lockRequest.getTaskId(), lock.hashCode());
            storeExternalValue(lockRequest.getTaskId(), false);
        } else {
            // 3. The lock is not acquired by any task. The current task is added to the
            // wanting list. We avoid of granting the lock to this task immidiatley to enble
            // this task to race with any other potential competitor. However, we assume that the task will acquire the
            // lock successfully, and store this information in the value tracker. If the scheduler decides to
            // resume this task as a winner of the race, then everything is fine. Otherwise, after the competitor won
            // the race, upon removing this task from wanting list, the stored information in the value tracker
            // will be updated to a failed lock acquire.
            LOGGER.debug("Task {} wants lock {}", lockRequest.getTaskId(), lock.hashCode());
            wantingTasks.putIfAbsent(lock, new HashSet<>());
            wantingTasks.get(lock).add(lockRequest);
            storeExternalValue(lockRequest.getTaskId(), true);
        }
        return false;
    }

    private void storeExternalValue(long id, Object value) {
        if (externalValueTracker.containsValue(id)) {
            LOGGER.error("Value for id {} already exists in external tracker. This should not happen.", id);
            throw new RuntimeException("Value for id " + id + " already exists");
        }

        LOGGER.debug("Storing value {} for id {}", value, id);
        externalValueTracker.setValue(id, value);
    }

    private void updateExternalValue(long id, Object value) {
        if (!externalValueTracker.containsValue(id)) {
            LOGGER.error("Value for id {} does not exist in external tracker. This should not happen.", id);
            throw new RuntimeException("Value for id " + id + " does not exist");
        }

        LOGGER.debug("Updating value {} for id {}", value, id);
        externalValueTracker.setValue(id, value);
    }

    private boolean isLockOwned(LockRequest lockRequest, Object lock) {
        Optional<Object> owner = activeTasks.get(lockRequest.getTaskId());
        if (owner.isPresent()) {
            if (owner.get() == lock) {
                LOGGER.debug("Lock already included by task {}", lockRequest.getTaskId());
                return true;
            }
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
        Set<LockRequest> wantingList = wantingTasks.get(lock);
        if (wantingList != null) {
            for (LockRequest wantingRequest : wantingList) {

                if (Objects.equals(wantingRequest.getTaskId(), taskId)) {
                    // Ignore the current task
                    continue;
                }

                // If the waiting task wants the lock upon a blocking acquire event, it must be added to the waiting tasks
                // and must be removed form the active tasks.
                if (wantingRequest.isBlocking()) {
                    waitingTasks.get(lock).add(wantingRequest);
                    activeTasks.remove(wantingRequest.getTaskId());
                } else {
                    // If the waiting task wants the lock upon a non-blocking acquire event, the external value
                    // tracker must be updated properly.
                    updateExternalValue(wantingRequest.getTaskId(), false);
                }
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
        Set<LockRequest> blockedRequests = waitingTasks.get(lock);
        wantingTasks.put(lock, new HashSet<>());
        if (blockedRequests != null) {
            for (LockRequest blockedRequest : blockedRequests) {
                activeTasks.put(blockedRequest.getTaskId(), Optional.empty());
                wantingTasks.get(lock).add(blockedRequest);
            }
            waitingTasks.remove(lock);
        }
    }

    @Override
    public void updateExternalValue(SchedulingChoice<?> task) {
        long id = task.getTaskId();
        if (externalValueTracker.containsValue(id)) {
            Object value = externalValueTracker.getValue(id);
            LOGGER.debug("Updating external value {} for task: {}", value, id);
            externalValueTracker.removeValue(id);

            PrimitiveValue val = new  PrimitiveValue(value);
            task.setValue(val);
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
        externalValueTracker.reset();
    }
}
