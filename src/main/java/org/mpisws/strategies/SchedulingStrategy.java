package org.mpisws.strategies;

import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.HaltTaskException;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.SchedulingChoice;

/**
 * The scheduling strategy is responsible for deciding which thread to schedule next.
 *
 * <p>It is used by the {@link org.mpisws.runtime.Scheduler} to decide which thread to schedule
 * next. The {@link org.mpisws.runtime.Scheduler} is in turn used by the {@link
 * org.mpisws.runtime.JmcRuntime} to manage the execution of threads.
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
    void initIteration(int iteration) throws HaltCheckerException;

    /**
     * Updates the strategy with the event that has occurred.
     *
     * <p>May be left empty if unused
     */
    void updateEvent(RuntimeEvent event) throws HaltTaskException, HaltExecutionException;

    /**
     * Returns the ID of the next thread to be scheduled.
     *
     * @return the ID of the next thread to be scheduled.
     */
    SchedulingChoice nextTask();

    /** Resets the strategy for the current Iteration. */
    void resetIteration();

    /** Teardown the strategy. Allows for releasing resources. */
    void teardown();
}
