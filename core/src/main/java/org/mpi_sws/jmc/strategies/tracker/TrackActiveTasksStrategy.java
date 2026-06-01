package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.strategies.SchedulingStrategy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abstract scheduling strategy that maintains the set of currently runnable ("active") tasks.
 *
 * <p>It delegates to a list of {@link Tracker}s, each watching a category of events (task lifecycle,
 * locks, wait/notify, static init, executors). On every event the runnable set is recomputed as the
 * <em>intersection</em> of all trackers' runnable sets, so any tracker can independently block a
 * task. Concrete strategies (e.g. {@link
 * org.mpi_sws.jmc.strategies.RandomSchedulingStrategy}) extend this class and only decide which of
 * the active tasks to schedule.
 */
public abstract class TrackActiveTasksStrategy implements SchedulingStrategy {

    /** Logger used to trace the computed active-task set. */
    private static final Logger LOGGER = LogManager.getLogger(TrackActiveTasksStrategy.class);

    /** Every task ID seen so far this iteration. */
    private final Set<Long> allTasks;
    /** The currently runnable subset of {@link #allTasks}. Guarded by {@link #tasksLock}. */
    private final Set<Long> activeTasks;
    /** Lock guarding {@link #allTasks} and {@link #activeTasks}. */
    private final Object tasksLock = new Object();

    /** The trackers consulted on every event; the active set is their intersection. */
    private final List<Tracker> trackers;

    /**
     * Constructs a new strategy with the default trackers: {@link TrackTasks}, {@link
     * TrackWaitNotify}, {@link TrackStaticInit}, and {@link TrackExecutors}.
     */
    public TrackActiveTasksStrategy() {
        this.allTasks = new HashSet<>();
        this.activeTasks = new HashSet<>();
        this.trackers = List.of(new TrackTasks(),
                new TrackWaitNotify(),
                new TrackStaticInit(),
                new TrackExecutors());
    }

    /**
     * Constructs a new strategy with a custom list of trackers.
     *
     * @param trackers the trackers whose runnable sets are intersected to form the active set
     */
    public TrackActiveTasksStrategy(List<Tracker> trackers) {
        this.allTasks = new HashSet<>();
        this.activeTasks = new HashSet<>();
        this.trackers = trackers;
    }

    /**
     * {@inheritDoc}
     *
     * <p>No-op at this level; subclasses may override to seed per-iteration state.
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
    }

    /**
     * Recomputes the active-task set from the given event.
     *
     * <p>Adds the event's task to {@link #allTasks}, then intersects that set with the runnable set
     * returned by each {@link Tracker} for this event. The resulting intersection becomes the new
     * {@link #activeTasks}.
     *
     * @param event the event to process
     */
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

    /** Clears the task sets and resets every tracker. */
    private void clear() {
        synchronized (tasksLock) {
            activeTasks.clear();
            allTasks.clear();
        }
        for (Tracker tracker : trackers) {
            tracker.reset();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears all task and tracker state for the next iteration.
     */
    @Override
    public void resetIteration(int iteration) {
        clear();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears all task and tracker state on shutdown.
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
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
