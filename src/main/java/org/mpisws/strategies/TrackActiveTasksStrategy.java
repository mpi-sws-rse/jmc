package org.mpisws.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A strategy that tracks the active tasks. */
public abstract class TrackActiveTasksStrategy implements SchedulingStrategy {

    private static final Logger LOGGER = LogManager.getLogger(TrackActiveTasksStrategy.class);

    private final Set<Long> allTasks;
    private final Set<Long> activeTasks;
    private final Object tasksLock = new Object();

    private final List<Tracker> trackers;

    /** Constructs a new TrackActiveTasksStrategy object. */
    public TrackActiveTasksStrategy() {
        this.allTasks = new HashSet<>();
        this.activeTasks = new HashSet<>();
        this.trackers = List.of(new TrackTasks(), new TrackLocks());
    }

    /** Constructs a new TrackActiveTasksStrategy object with the given trackers. */
    public TrackActiveTasksStrategy(List<Tracker> trackers) {
        this.allTasks = new HashSet<>();
        this.activeTasks = new HashSet<>();
        this.trackers = trackers;
    }

    @Override
    public void initIteration(int iteration) {}

    @Override
    public void updateEvent(RuntimeEvent event) {
        Set<Long> localActiveTasks;
        synchronized (tasksLock) {
            allTasks.add(event.getTaskId());
            localActiveTasks = new HashSet<>(this.allTasks);
        }
        for (Tracker tracker : trackers) {
            localActiveTasks.retainAll(tracker.updateEvent(event));
        }

        LOGGER.debug("Active tasks: {}", localActiveTasks);
        synchronized (tasksLock) {
            this.activeTasks.clear();
            this.activeTasks.addAll(localActiveTasks);
        }
    }

    private void clear() {
        synchronized (tasksLock) {
            activeTasks.clear();
            allTasks.clear();
        }
        for (Tracker tracker : trackers) {
            tracker.reset();
        }
    }

    @Override
    public void resetIteration(int iteration) {
        clear();
    }

    @Override
    public void teardown() {
        clear();
    }

    /**
     * Returns whether the given thread is active.
     *
     * @param threadId the thread ID
     * @return whether the given thread is active
     */
    protected Boolean isActive(Long threadId) {
        synchronized (tasksLock) {
            return activeTasks.contains(threadId);
        }
    }

    /**
     * Returns the set of active tasks.
     *
     * @return the set of active tasks
     */
    protected Set<Long> getActiveTasks() {
        synchronized (tasksLock) {
            return new HashSet<>(activeTasks);
        }
    }

    /**
     * Marks the given thread as active.
     *
     * @param threadId the thread ID
     */
    protected void markActive(Long threadId) {
        synchronized (tasksLock) {
            activeTasks.add(threadId);
        }
    }

    /**
     * Marks the given thread as inactive.
     *
     * @param threadId the thread ID
     */
    protected void markInactive(Long threadId) {
        synchronized (tasksLock) {
            activeTasks.remove(threadId);
        }
    }

    /** Tracks the active tasks based on events. */
    public interface Tracker {
        /**
         * Updates the event.
         *
         * @param event the event to update
         * @return the set of active tasks
         */
        Set<Long> updateEvent(RuntimeEvent event);

        /** Resets the tracker. */
        void reset();
    }

    /** Tracks the tasks start finish and join request events. */
    public static class TrackTasks implements Tracker {
        private final Set<Long> activeTasks;
        private final Map<Long, Set<Long>> waitingTasks;
        private final Object tasksLock = new Object();

        /** Constructs a new TrackTasks object. */
        public TrackTasks() {
            this.activeTasks = new HashSet<>();
            this.waitingTasks = new ConcurrentHashMap<>();
        }

        @Override
        public Set<Long> updateEvent(RuntimeEvent event) {
            if (event.getType() == RuntimeEventType.START_EVENT) {
                synchronized (tasksLock) {
                    activeTasks.add(event.getTaskId());
                }
            } else if (event.getType() == RuntimeEventType.FINISH_EVENT) {
                Long eventTask = event.getTaskId();
                synchronized (tasksLock) {
                    activeTasks.remove(eventTask);
                    Set<Long> waitingList = waitingTasks.get(eventTask);
                    if (waitingList != null) {
                        activeTasks.addAll(waitingList);
                        waitingTasks.remove(eventTask);
                    }
                }
            } else if (event.getType() == RuntimeEventType.JOIN_REQUEST_EVENT) {
                Long requestingTask = event.getTaskId();
                Long requestedTask = event.getParam("waitingTask");

                synchronized (tasksLock) {
                    // If the requested task is active, mark the requesting task as waiting
                    if (activeTasks.contains(requestedTask)) {
                        Set<Long> waitingList =
                                waitingTasks.computeIfAbsent(requestedTask, k -> new HashSet<>());
                        waitingList.add(requestingTask);
                        activeTasks.remove(requestingTask);
                    }
                }
            }
            return getActiveTasks();
        }

        private Set<Long> getActiveTasks() {
            synchronized (tasksLock) {
                return new HashSet<>(activeTasks);
            }
        }

        @Override
        public void reset() {
            synchronized (tasksLock) {
                activeTasks.clear();
                waitingTasks.clear();
            }
        }
    }

    /** Tracks the locks acquired and released events of tasks. */
    public static class TrackLocks implements Tracker {

        /**
         * For each lock, Contains a list of tasks that want the lock. Once the task acquires the
         * lock, it is removed from the set.
         */
        private final Map<Object, Set<Long>> waitingTasks;

        private final Map<Long, Optional<Object>> activeTasks;

        private final Set<Long> blockedTasks;
        private final Object tasksLock = new Object();

        /** Constructs a new TrackLocks object. */
        public TrackLocks() {
            this.waitingTasks = new ConcurrentHashMap<>();
            this.blockedTasks = new HashSet<>();
            this.activeTasks = new ConcurrentHashMap<>();
        }

        /**
         * Updates based on lock acquire and release events.
         *
         * <p>For every acquire event, if the lock is already acquired, the task is blocked. Tracked
         * in both {@link TrackLocks#waitingTasks} and {@link TrackLocks#blockedTasks}.
         *
         * <p>For every release event, the corresponding waiting tasks are marked as active.
         *
         * @param event the event to update
         * @return the set of active tasks
         */
        @Override
        public Set<Long> updateEvent(RuntimeEvent event) {
            // If the task is not blocked, mark it as active
            if (!blockedTasks.contains(event.getTaskId())) {
                activeTasks.put(event.getTaskId(), Optional.empty());
            }

            if (event.getType() == RuntimeEventType.LOCK_ACQUIRE_EVENT) {
                // If the lock is already acquired, block the task.
                Object lock = event.getParam("lock");
                Long taskId = event.getTaskId();
                Optional<Object> owner = activeTasks.get(taskId);
                if (owner != null && owner.isPresent()) {
                    // Check who holds the lock to enable reentrancy
                    if (owner.get() == lock) {
                        return getActiveTasks();
                    }
                }
                Set<Long> tasks = waitingTasks.computeIfAbsent(lock, k -> new HashSet<>());
                tasks.add(taskId);
                if (owner != null && owner.isPresent()) {
                    // If the lock is already acquired, mark the task as blocked
                    activeTasks.remove(taskId);
                    synchronized (tasksLock) {
                        blockedTasks.add(taskId);
                    }
                }
            } else if (event.getType() == RuntimeEventType.LOCK_ACQUIRED_EVENT) {
                // If the lock is acquired, mark the other waiting tasks as inactive.
                // Remove the current task from the waiting tasks.
                Object lock = event.getParam("lock");
                Long taskId = event.getTaskId();
                // For re-entrant locks, the lock owner is the task itself
                activeTasks.put(taskId, Optional.of(lock));
                synchronized (tasksLock) {
                    blockedTasks.remove(taskId);
                }
                if (waitingTasks.containsKey(lock)) {
                    Set<Long> tasks = waitingTasks.get(lock);
                    tasks.remove(taskId);
                    if (tasks.isEmpty()) {
                        waitingTasks.remove(lock);
                    } else {
                        synchronized (tasksLock) {
                            for (Long task : tasks) {
                                activeTasks.remove(task);
                                blockedTasks.add(task);
                            }
                        }
                    }
                }
            } else if (event.getType() == RuntimeEventType.LOCK_RELEASE_EVENT) {
                // If the lock is released, mark the waiting tasks as active.
                Object lock = event.getParam("lock");
                Set<Long> blockedTasks = waitingTasks.get(lock);
                if (blockedTasks != null) {
                    for (Long blockedTask : blockedTasks) {
                        activeTasks.put(blockedTask, Optional.empty());
                        synchronized (tasksLock) {
                            this.blockedTasks.remove(blockedTask);
                        }
                    }
                }
            }
            return getActiveTasks();
        }

        private Set<Long> getActiveTasks() {
            return new HashSet<>(activeTasks.keySet());
        }

        @Override
        public void reset() {
            synchronized (tasksLock) {
                activeTasks.clear();
                waitingTasks.clear();
                blockedTasks.clear();
            }
        }
    }
}
