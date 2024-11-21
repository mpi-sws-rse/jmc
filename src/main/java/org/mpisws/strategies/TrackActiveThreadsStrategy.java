package org.mpisws.strategies;

import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashSet;
import java.util.Set;

public abstract class TrackActiveThreadsStrategy implements SchedulingStrategy {
    public abstract Long nextTask();

    private final Set<Long> activeThreads;
    private final Object threadsLock = new Object();

    public TrackActiveThreadsStrategy() {
        this.activeThreads = new HashSet<>();
    }

    @Override
    public void updateEvent(RuntimeEvent event) {
        if (event.getType() == RuntimeEventType.START_EVENT) {
            synchronized (threadsLock) {
                activeThreads.add(event.getTaskId());
            }
        } else if (event.getType() == RuntimeEventType.FINISH_EVENT) {
            synchronized (threadsLock) {
                activeThreads.remove(event.getTaskId());
            }
        }
    }

    @Override
    public void reset() {
        synchronized (threadsLock) {
            activeThreads.clear();
        }
    }

    protected Boolean isActive(Long threadId) {
        synchronized (threadsLock) {
            return activeThreads.contains(threadId);
        }
    }

    protected Set<Long> getActiveThreads() {
        synchronized (threadsLock) {
            return new HashSet<>(activeThreads);
        }
    }
}
