package org.mpisws.jmc.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.RuntimeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A strategy that tracks the active tasks.
 */
public abstract class TrackActiveTasksStrategy implements SchedulingStrategy {

    private static final Logger LOGGER = LogManager.getLogger(TrackActiveTasksStrategy.class);

    private final Set<Long> allTasks;
    private final Set<Long> activeTasks;
    private final Object tasksLock = new Object();

    private final List<Tracker> trackers;

    /**
     * Constructs a new TrackActiveTasksStrategy object.
     */
    public TrackActiveTasksStrategy() {
        this.allTasks = new HashSet<>();
        this.activeTasks = new HashSet<>();
        this.trackers = List.of(new TrackTasks(), new TrackLocks());
    }

    /**
     * Constructs a new TrackActiveTasksStrategy object with the given trackers.
     */
    public TrackActiveTasksStrategy(List<Tracker> trackers) {
        this.allTasks = new HashSet<>();
        this.activeTasks = new HashSet<>();
        this.trackers = trackers;
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
    }

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

        LOGGER.debug("Active tasks: {}", new HashSet<>(localActiveTasks));
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

    /**
     * Tracks the active tasks based on events.
     */
    public interface Tracker {
        /**
         * Updates the event.
         *
         * @param event the event to update
         * @return the set of active tasks
         */
        Set<Long> updateEvent(RuntimeEvent event);

        /**
         * Resets the tracker.
         */
        void reset();
    }

    /**
     * Tracks the tasks start finish and join request events.
     */
    public static class TrackTasks implements Tracker {
        private final Set<Long> activeTasks;
        private final Map<Long, Set<Long>> waitingTasks;
        private final Set<Long> completedTasks;
        private final Object tasksLock = new Object();

        /**
         * Constructs a new TrackTasks object.
         */
        public TrackTasks() {
            this.activeTasks = new HashSet<>();
            this.completedTasks = new HashSet<>();
            this.waitingTasks = new ConcurrentHashMap<>();
        }

        @Override
        public Set<Long> updateEvent(RuntimeEvent event) {
            if (event.getType() == RuntimeEvent.Type.START_EVENT) {
                synchronized (tasksLock) {
                    activeTasks.add(event.getTaskId());
                }
            } else if (event.getType() == RuntimeEvent.Type.FINISH_EVENT) {
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
            } else if (event.getType() == RuntimeEvent.Type.JOIN_REQUEST_EVENT) {
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
                completedTasks.clear();
            }
        }
    }

    /**
     * Tracks the locks acquired and released events of tasks.
     */
    public static class TrackLocks implements Tracker {

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
         * <p>For every acquire event, if the lock is already acquired, the task is made to wait. Tracked
         * in {@link TrackLocks#waitingTasks}.
         *
         * <p>If it is not yet acquired, it is put in {@link TrackLocks#wantingTasks} and retained in active tasks.
         * </p>
         *
         * <p>For every release event, the corresponding waiting tasks are marked as active.
         *
         * @param event the event to update
         * @return the set of active tasks
         */
        @Override
        public Set<Long> updateEvent(RuntimeEvent event) {

            Long taskId = event.getTaskId();
            if (taskId == null) {
                // Ignore events without a task ID
                return getActiveTasks();
            }
            activeTasks.putIfAbsent(taskId, Optional.empty());

            RuntimeEvent.Type type = event.getType();

            if (type == RuntimeEvent.Type.LOCK_ACQUIRE_EVENT) {
                Object lock = event.getParam("lock");
                // Want the lock. Three cases.
                // 1. Current task already has the lock. Ignore.
                Optional<Object> owner = activeTasks.get(taskId);
                if (owner != null && owner.isPresent()) {
                    if (owner.get() == lock) {
                        LOGGER.debug("Reentrant lock already included by task {}", taskId);
                        return getActiveTasks();
                    }
                }
                // 2. The lock is already acquired by another task. The current task is added to the
                // waiting list.
                if (waitingTasks.containsKey(lock)) {
                    Set<Long> tasks = waitingTasks.get(lock);
                    tasks.add(taskId);
                    activeTasks.remove(taskId);
                } else {
                    // 3. The lock is not acquired by any task. The current task is added to the wanting
                    // list.
                    wantingTasks.putIfAbsent(lock, new HashSet<>());
                    wantingTasks.get(lock).add(taskId);
                }
            } else if (type == RuntimeEvent.Type.LOCK_ACQUIRED_EVENT) {
                Object lock = event.getParam("lock");
                // The lock is acquired by the current task. Remove it from the wanting list and add the rest to waiting
                // list.
                activeTasks.put(taskId, Optional.of(lock));
                waitingTasks.putIfAbsent(lock, new HashSet<>());
                Set<Long> wantingList = wantingTasks.get(lock);
                if (wantingList != null) {
                    for (Long wantingTask : wantingList) {
                        // If the task is not already in the waiting list, add it to the waiting list
                        if (Objects.equals(wantingTask, taskId)) {
                            // Ignore the current task
                            continue;
                        }
                        waitingTasks.get(lock).add(wantingTask);
                        activeTasks.remove(wantingTask);
                    }
                    wantingTasks.remove(lock);
                }
            } else if (type == RuntimeEvent.Type.LOCK_RELEASE_EVENT) {
                Object lock = event.getParam("lock");
                // The lock is released. The waiting tasks are marked as active.
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
            return new HashSet<>(activeTasks.keySet());
        }

        @Override
        public void reset() {
            activeTasks.clear();
            waitingTasks.clear();
            wantingTasks.clear();
        }
    }
}
