package org.mpi_sws.jmc.strategies;

import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.scheduling.Scheduler;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

/**
 * The scheduling strategy is responsible for deciding which thread to schedule next.
 *
 * <p>It is used by the {@link Scheduler} to decide which thread to schedule next. The {@link
 * Scheduler} is in turn used by the {@link JmcRuntime} to manage the execution of threads.
 *
 * <p>Implementations of this interface should be thread-safe. Multiple threads can make concurrent
 * calls to the {@link SchedulingStrategy#updateEvent} function.
 */
public interface SchedulingStrategy {
    /**
     * Initializes the strategy for a new iteration.
     *
     * @param iteration the number of the iteration.
     */
    void initIteration(int iteration, JmcModelCheckerReport report) throws HaltCheckerException;

    /**
     * Updates the strategy with the event that has occurred.
     *
     * <p>May be left empty if unused
     */
    void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException;

    /**
     * Returns the ID of the next thread to be scheduled.
     *
     * @return the ID of the next thread to be scheduled.
     */
    SchedulingChoice<?> nextTask();

    /** Resets the strategy for the current Iteration. */
    void resetIteration(int iteration);

    /** Teardown the strategy. Allows for releasing resources. */
    void teardown();
}
