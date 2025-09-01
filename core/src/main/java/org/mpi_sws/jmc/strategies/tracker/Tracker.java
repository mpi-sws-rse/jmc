package org.mpi_sws.jmc.strategies.tracker;

import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.Set;

/** Tracks the active tasks based on events. */
public interface Tracker {
    /**
     * Updates the event.
     *
     * @param event the event to update
     * @return the set of active tasks
     */
    Set<Long> updateEvent(JmcRuntimeEvent event) throws HaltCheckerException;

    /** Resets the tracker. */
    void reset();
}
