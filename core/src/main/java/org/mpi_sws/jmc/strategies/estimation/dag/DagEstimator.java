package org.mpi_sws.jmc.strategies.estimation.dag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.estimation.MetaGraphEstimator;
import org.mpi_sws.jmc.strategies.trust.*;

import java.util.List;
import java.util.Set;

/**
 * PeStor — Pitt's DAG estimator (Algorithm P) for the {@code pestor} strategy.
 *
 * <p>Estimates {@code C(P)} by a random walk over the transition system {@code T(P)}. At each step it
 * multiplies the running estimate {@link #expectedValue} by {@code out/in}, where {@code out} is the
 * number of successors (enabled threads) and {@code in} is the number of predecessors of the current
 * graph. By Pitt's correction, dividing by the in-degree compensates for the fact that {@code T(P)}
 * is a DAG (different schedules can reach the same graph), keeping the estimator unbiased.
 *
 * <p>The number of predecessors equals the number of <em>sequentially-maximal</em> events of the
 * graph, which is computed in poly time from the current graph maintained in an {@link
 * ExecutionGraphSimulator}. Driven by {@link DagEstimationStrategy}.
 */
public class DagEstimator implements MetaGraphEstimator {

    private static final Logger LOGGER = LogManager.getLogger(DagEstimator.class);

    /** The naive execution graph of the current walk (reads observe the coherence-maximal write). */
    protected final ExecutionGraphSimulator executionGraph;

    /** The running product estimate for the current trial (Pitt's {@code ∏ out/in}); starts at 1. */
    protected float expectedValue = 1f;

    /** Creates an estimator with an empty simulator graph. */
    public DagEstimator() {
        this.executionGraph = new ExecutionGraphSimulator();
    }

    /**
     * Applies the current step's events to the simulator graph and updates the estimate using the
     * last event.
     *
     * @param events the trust events of the current runtime event.
     * @param activeTasks the currently runnable tasks (the successors of the current graph).
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    public void updateEvent(List<Event> events, Set<Long> activeTasks) throws HaltTaskException, HaltExecutionException {

        if (!events.isEmpty() && activeTasks.size() != 0) {
            // The lock acquisition and release events, will be compiled into a pair of ReadEx and WriteEx events
            for (Event e : events) {
                LOGGER.debug("Received event: {}", e);
                executionGraph.updateEvent(e);
            }

            // Update the estimation based on the last event
            Event e = events.get(events.size() - 1);
            updateEstimation(e, activeTasks);
        }
    }

    /**
     * Multiplies the estimate by {@code out/in} for the just-added event {@code e}.
     *
     * <p>{@code out} = number of enabled threads (successors). {@code in} = number of
     * sequentially-maximal events (predecessors): {@code e} itself (always sc-maximal) plus each
     * program-order-maximal event of a <em>different</em> task that is {@link #isScMax} and does not
     * {@link #conflict} with {@code e}.
     *
     * @param e the event just added.
     * @param activeTasks the currently runnable tasks.
     */
    protected void updateEstimation(Event e, Set<Long> activeTasks) {
        int in = 1;
        int out = activeTasks.size();
        List<Event> poMax = executionGraph.getAllPoMaxEvents();
        for (Event poMaxEvent : poMax) {
            if (poMaxEvent.getTaskId() != e.getTaskId() && isScMax(poMaxEvent)) {
                if (!conflict(poMaxEvent, e)) {
                    in++;
                }
            }
        }

        expectedValue = expectedValue * out / in;
        LOGGER.debug("Expected value: {}", expectedValue);
    }

    /**
     * Returns whether the given (already program-order-maximal) event is sequentially maximal — i.e.
     * maximal in the SC order: co-maximal ∧ reads-from-maximal ∧ from-read-maximal ∧
     * thread-creation-maximal ∧ thread-start-maximal ∧ thread-join-maximal.
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
     * Returns whether the just-added event {@code e2} gives {@code e1} an SC successor (so {@code e1}
     * is no longer sequentially maximal and must not be counted as a predecessor).
     *
     * <p>Two writes to the same location conflict; a thread-start {@code e1} conflicts if it started
     * {@code e2}'s task or is not start-maximal-with-starter. Otherwise there is no conflict.
     *
     * @param e1 a candidate sequentially-maximal event.
     * @param e2 the just-added event.
     * @return true if {@code e1} conflicts with {@code e2}.
     */
    protected boolean conflict(Event e1, Event e2) {
        if (!EventUtils.isWrite(e1) || !EventUtils.isWrite(e2)) {
            if (EventUtils.isThreadStart(e1)) {
                long startedBy = EventUtils.getStartedBy(e1);
                // We need to check if the START event is PO-MAX regarding the PO-MAX of the starter thread
                return startedBy == e2.getTaskId() || !executionGraph.isStartMaxWithStarter(e1);
            }
            /*if (EventUtils.isThreadFinish(e2)) {
                long tid = e2.getTaskId();
                // get the tid of the thread which started the e2's thread
                long startedBy = executionGraph.getStarterTid(tid);
                LOGGER.debug("Started by: {}", startedBy);
                Event lastEventOfStartedBy = executionGraph.getLastEventOfTask(startedBy);
                return EventUtils.isJoinRequest(lastEventOfStartedBy);
            }*/
        } else { // One of the two events is a write event
            return e1.getLocation().equals(e2.getLocation());
        }

        // No conflict found
        return false;
    }

    /**
     * Returns the current trial's point estimate.
     *
     * @return the running {@code ∏ out/in} product.
     */
    public float getExpectedValue() {
        return expectedValue;
    }

    /** Resets the estimate to 1 and clears the simulator graph, starting a fresh trial. */
    public void reset() {
        expectedValue = 1f;
        executionGraph.reset();
    }
}
