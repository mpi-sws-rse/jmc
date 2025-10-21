package org.mpi_sws.jmc.strategies.estimation;

import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.trust.Event;

import java.util.List;
import java.util.Set;

public interface MetaGraphEstimator {

    void updateEvent(List<Event> events, Set<Long> activeTasks) throws HaltTaskException, HaltExecutionException;

    float getExpectedValue();

    void reset();
}
