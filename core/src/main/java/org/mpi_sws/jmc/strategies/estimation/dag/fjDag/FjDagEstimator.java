package org.mpi_sws.jmc.strategies.estimation.dag.fjDag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.estimation.MetaGraphEstimator;
import org.mpi_sws.jmc.strategies.trust.Event;
import org.mpi_sws.jmc.strategies.trust.EventUtils;
import org.mpi_sws.jmc.strategies.trust.ExecutionGraphSimulator;

import java.util.List;
import java.util.Set;

/**
 * Fork-join PeStor — the fork-join specialization of the DAG estimator ({@code fj-pestor}).
 *
 * <p>Like {@link org.mpi_sws.jmc.strategies.estimation.dag.DagEstimator} it computes the running
 * {@code ∏ out/in} estimate over {@code T(P)}, but for <b>fork-join</b> programs (one main thread
 * spawns all workers and later joins them) it deliberately <em>does not charge the estimator</em>
 * for the thread-creation and join accounting, which otherwise inflates variance. Concretely it
 * ignores the entire fork phase, all main-task events, and thread-lifecycle/noop events; only the
 * genuine worker memory accesses contribute to the estimate. Driven by {@link
 * FjDagEstimationStrategy}, which schedules the program so this structure holds.
 */
public class FjDagEstimator implements MetaGraphEstimator {

    private static final Logger LOGGER = LogManager.getLogger(FjDagEstimator.class);

    /** The naive execution graph of the current walk. */
    protected final ExecutionGraphSimulator executionGraph;

    /** The running product estimate for the current trial; starts at 1. */
    protected float expectedValue = 1f;

    /** True once the main task has finished creating all threads (its first {@code join-req} seen). */
    private boolean forkComplete = false;

    /** Creates an estimator with an empty simulator graph. */
    public FjDagEstimator() {
        this.executionGraph = new ExecutionGraphSimulator();
    }

    /**
     * Applies the current step's events to the simulator graph and updates the estimate, skipping
     * the fork phase and main-task events.
     *
     * <p>While the main task (task 0) is still forking, nothing is charged; {@link #forkComplete}
     * flips to true when the main task issues its first {@code join-req}. After that, main-task
     * events are ignored and the estimate is updated only for worker events.
     *
     * @param events the trust events of the current runtime event.
     * @param activeTasks the currently runnable tasks.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    @Override
    public void updateEvent(List<Event> events, Set<Long> activeTasks) throws HaltTaskException, HaltExecutionException {
        if (!events.isEmpty() && activeTasks.size() != 0) {
            // The lock acquisition and release events, will be compiled into a pair of ReadEx and WriteEx events
            for (Event e : events) {
                LOGGER.debug("Received event: {}", e);
                executionGraph.updateEvent(e);
            }

            // Update the estimation based on the last event
            Event e = events.get(events.size() - 1);

            if (!forkComplete) {
                if (EventUtils.isJoinRequest(e) && e.getTaskId() == 0) {
                    // The main task finished forking and is now starting to join
                    forkComplete = true;
                }
                // Since the main task is still forking, we do not update the estimation
                return;
            }
            if (e.getTaskId() == 0) {
                // We do not update the estimation based on the main task events
                return;
            }
            updateEstimation(e, activeTasks);
        }
    }

    /**
     * Multiplies the estimate by {@code out/in} for a worker event {@code e}, excluding the main
     * task and thread-lifecycle events.
     *
     * <p>Thread-start events are skipped; the main task is removed from {@code activeTasks}; {@code
     * out} is the remaining enabled workers and {@code in} counts the sequentially-maximal
     * <em>non-noop</em> po-max events of the worker tasks (excluding the main task and {@code e}'s
     * task) that are {@link #isScMax} and non-{@link #conflict}ing.
     *
     * @param e the worker event just added.
     * @param activeTasks the currently runnable tasks.
     */
    protected void updateEstimation(Event e, Set<Long> activeTasks) {
        if (EventUtils.isThreadStart(e)) {
            // If the event is a thread finish event or a thread start event, we do not consider it in the estimation
            return;
        }
        // If the main task is still active, we do not consider it in the estimation
        activeTasks.remove(1L);
        if (activeTasks.size() > 0) {
            int in = 1;
            int out = activeTasks.size();
            List<Event> poMax = executionGraph.getAllNonNoopPoMaxEvents();
            for (Event poMaxEvent : poMax) {
                if (poMaxEvent.getTaskId() != 0L &&
                        poMaxEvent.getTaskId() != e.getTaskId() &&
                        isScMax(poMaxEvent)) {
                    if (!conflict(poMaxEvent, e)) {
                        in++;
                    }
                }
            }
            expectedValue = expectedValue * out / in;
            LOGGER.debug("Expected value: {}", expectedValue);
        }
    }

    /**
     * Returns whether {@code e} is sequentially maximal (co ∧ rf ∧ fr ∧ tc ∧ st ∧ jt-maximal).
     *
     * @param e a program-order-maximal event.
     * @return true if {@code e} has no SC successor.
     */
    protected boolean isScMax(Event e) {
        return executionGraph.isCoMax(e) &&
                executionGraph.isRfMax(e) &&
                executionGraph.isFrMax(e) &&
                executionGraph.isTcMax(e) &&
                executionGraph.isStMax(e) &&
                executionGraph.isJtMax(e);
    }

    /**
     * Returns whether {@code e1} and {@code e2} conflict — here, only two writes to the same
     * location (thread-lifecycle conflicts are irrelevant since the estimator ignores them in
     * fork-join mode).
     *
     * @param e1 a candidate sequentially-maximal event.
     * @param e2 the just-added event.
     * @return true if both are writes to the same location.
     */
    protected boolean conflict(Event e1, Event e2) {
        if (!EventUtils.isWrite(e1) || !EventUtils.isWrite(e2)) {
            return false;
        } else { // One of the two events is a write event
            return e1.getLocation().equals(e2.getLocation());
        }
    }

    /**
     * Returns the current trial's point estimate.
     *
     * @return the running {@code ∏ out/in} product.
     */
    @Override
    public float getExpectedValue() {
        return expectedValue;
    }

    /** Resets the estimate, fork flag, and simulator graph for a fresh trial. */
    @Override
    public void reset() {
        forkComplete = false;
        expectedValue = 1f;
        executionGraph.reset();
    }

    /**
     * Returns whether the main task has finished forking (used by the strategy's scheduler).
     *
     * @return true once forking is complete.
     */
    public boolean isForkComplete() {
        return forkComplete;
    }
}
