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

public class FjDagEstimator implements MetaGraphEstimator {

    private static final Logger LOGGER = LogManager.getLogger(FjDagEstimator.class);

    protected final ExecutionGraphSimulator executionGraph;

    protected float expectedValue = 1f;

    private boolean forkComplete = false;

    public FjDagEstimator() {
        this.executionGraph = new ExecutionGraphSimulator();
    }

    /**
     * @param events
     * @throws HaltTaskException
     * @throws HaltExecutionException
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

    protected boolean isScMax(Event e) {
        return executionGraph.isCoMax(e) &&
                executionGraph.isRfMax(e) &&
                executionGraph.isFrMax(e) &&
                executionGraph.isTcMax(e) &&
                executionGraph.isStMax(e) &&
                executionGraph.isJtMax(e);
    }

    protected boolean conflict(Event e1, Event e2) {
        if (!EventUtils.isWrite(e1) || !EventUtils.isWrite(e2)) {
            return false;
        } else { // One of the two events is a write event
            return e1.getLocation().equals(e2.getLocation());
        }
    }

    /**
     * @return
     */
    @Override
    public float getExpectedValue() {
        return expectedValue;
    }

    /**
     *
     */
    @Override
    public void reset() {
        forkComplete = false;
        expectedValue = 1f;
        executionGraph.reset();
    }

    public boolean isForkComplete() {
        return forkComplete;
    }
}
