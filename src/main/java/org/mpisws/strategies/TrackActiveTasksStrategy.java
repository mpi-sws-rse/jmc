package org.mpisws.strategies;

import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashSet;
import java.util.Set;

/** A strategy that tracks the active tasks. */
public abstract class TrackActiveTasksStrategy implements SchedulingStrategy {

    private final Set<Long> activeTasks;
    private final Object tasksLock = new Object();

    /** Constructs a new TrackActiveTasksStrategy object. */
    public TrackActiveTasksStrategy() {
        this.activeTasks = new HashSet<>();
    }

    @Override
    public void updateEvent(RuntimeEvent event) {
        if (event.getType() == RuntimeEventType.START_EVENT) {
            synchronized (tasksLock) {
                activeTasks.add(event.getTaskId());
            }
        } else if (event.getType() == RuntimeEventType.FINISH_EVENT) {
            synchronized (tasksLock) {
                activeTasks.remove(event.getTaskId());
            }
        }
    }

    @Override
    public void reset() {
        synchronized (tasksLock) {
            activeTasks.clear();
        }
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
