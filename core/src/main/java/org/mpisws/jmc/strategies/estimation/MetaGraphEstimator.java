package org.mpisws.jmc.strategies.estimation;

import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.trust.Event;

import java.util.List;
import java.util.Set;

public interface MetaGraphEstimator {

    void updateEvent(List<Event> events, Set<Long> activeTasks) throws HaltTaskException, HaltExecutionException;

    float getExpectedValue();

    void reset();
}
