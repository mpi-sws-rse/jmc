package org.mpi_sws.jmc.strategies.tracker;

import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.Set;

/** Tracks the active tasks based on events. */
public interface Tracker {
    /**
     * Processes an event and returns the tasks this tracker currently considers runnable.
     *
     * <p>The owning {@link TrackActiveTasksStrategy} intersects the returned set with those of the
     * other trackers, so a tracker effectively blocks any task it omits.
     *
     * @param event the event to process
     * @return the set of tasks this tracker considers runnable
     * @throws HaltCheckerException if the tracker detects a condition that must stop the whole check
     */
    Set<Long> updateEvent(JmcRuntimeEvent event) throws HaltCheckerException;

    /** Resets the tracker. */
    void reset();
}
