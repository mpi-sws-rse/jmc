package org.mpisws.strategies;

import org.mpisws.runtime.RuntimeEvent;

/**
 * The scheduling strategy is responsible for deciding which thread to schedule next.
 *
 * <p>It is used by the {@link org.mpisws.runtime.Scheduler} to decide which thread to schedule next.
 * The {@link org.mpisws.runtime.Scheduler} is in turn used by the {@link org.mpisws.runtime.JmcRuntime} to
 * manage the execution of threads.
 * </p>
 *
 * <p>Implementations of this interface should be thread-safe. Multiple threads can make concurrent calls
 * to the {@link SchedulingStrategy#updateEvent} function.</p>
 */
public interface SchedulingStrategy {

    /**
     * Updates the strategy with the event that has occurred.
     *
     * <p>May be left empty if unused</p>
     */
    void updateEvent(RuntimeEvent event);

    /**
     * Returns the ID of the next thread to be scheduled.
     *
     * @return the ID of the next thread to be scheduled.
     */
    Long nextThread();

    /**
     * Resets the strategy.
     */
    void reset();
}
