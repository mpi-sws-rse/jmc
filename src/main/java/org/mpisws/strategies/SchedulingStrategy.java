package org.mpisws.strategies;

import org.mpisws.runtime.RuntimeEvent;

public interface SchedulingStrategy {
    void updateEvent(RuntimeEvent event);

    Long nextThread();

    void addThread(Long threadId);
}
