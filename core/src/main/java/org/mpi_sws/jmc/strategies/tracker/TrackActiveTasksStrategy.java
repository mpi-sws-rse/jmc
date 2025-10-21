package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.strategies.SchedulingStrategy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        this.trackers = List.of(new TrackTasks(), new TrackWaitNotify());
    }

    /** Constructs a new TrackActiveTasksStrategy object with the given trackers. */
    public TrackActiveTasksStrategy(List<Tracker> trackers) {
        this.allTasks = new HashSet<>();
        this.activeTasks = new HashSet<>();
        this.trackers = trackers;
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {}

    @Override
    public void updateEvent(JmcRuntimeEvent event) {
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
}
