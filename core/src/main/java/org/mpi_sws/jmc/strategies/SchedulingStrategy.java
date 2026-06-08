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
 * <p>Because JMC serializes execution, a strategy is never entered concurrently: {@link
 * SchedulingStrategy#updateEvent} runs on the single currently-running task, while {@link
 * SchedulingStrategy#nextTask} runs on the scheduler thread while all program tasks are paused. The
 * two therefore alternate rather than overlap.
 */
public interface SchedulingStrategy {
    /**
     * Initializes the strategy for a new iteration.
     *
     * @param iteration the number of the iteration
     * @param report the model checker report for the run
     * @throws HaltCheckerException if the strategy decides the whole check must stop
     */
    void initIteration(int iteration, JmcModelCheckerReport report) throws HaltCheckerException;

    /**
     * Updates the strategy with an event that has occurred.
     *
     * <p>May be left empty if the strategy does not consume events.
     *
     * @param event the event that occurred
     * @throws HaltTaskException if the originating task must be halted
     * @throws HaltExecutionException if the current execution must be halted
     */
    void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException;

    /**
     * Returns the next scheduling choice to apply.
     *
     * <p>May return {@code null} if no task is runnable yet; the scheduler thread retries in that
     * case.
     *
     * @return the next {@link SchedulingChoice}, or {@code null} if none is available
     */
    SchedulingChoice<?> nextTask();

    /**
     * Resets the strategy's per-iteration state.
     *
     * @param iteration the number of the iteration being reset
     */
    void resetIteration(int iteration);

    /**
     * Tears down the strategy, allowing it to release any resources.
     *
     * @param report the model checker report for the run
     */
    void teardown(JmcModelCheckerReport report);
}
