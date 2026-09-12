package org.mpi_sws.jmc.strategies.estimation;

import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.trust.Event;

import java.util.List;
import java.util.Set;

/**
 * The estimator contract for the <b>DAG</b> family (PeStor, fork-join PeStor).
 *
 * <p>A DAG estimator samples the transition system {@code T(P)} — a DAG whose sinks are the maximal
 * execution graphs — by a random walk driven by the random scheduler. It is updated event-by-event
 * as the walk proceeds and maintains a running point estimate of {@code C(P)}. Implemented by {@code
 * DagEstimator} and {@code FjDagEstimator}.
 */
public interface MetaGraphEstimator {

    /**
     * Feeds the events of the current scheduling step to the estimator, updating the running
     * estimate.
     *
     * @param events the trust events produced by the current runtime event.
     * @param activeTasks the currently runnable tasks (the graph's successors / enabled threads).
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    void updateEvent(List<Event> events, Set<Long> activeTasks) throws HaltTaskException, HaltExecutionException;

    /**
     * Returns the current trial's point estimate (the running product accumulated along the walk).
     *
     * @return the estimate.
     */
    float getExpectedValue();

    /** Resets the estimator to start a fresh trial. */
    void reset();
}
