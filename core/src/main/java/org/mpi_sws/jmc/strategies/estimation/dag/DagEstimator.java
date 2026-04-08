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

public class DagEstimator implements MetaGraphEstimator {

    private static final Logger LOGGER = LogManager.getLogger(DagEstimator.class);

    protected final ExecutionGraphSimulator executionGraph;

    protected float expectedValue = 1f;

    public DagEstimator() {
        this.executionGraph = new ExecutionGraphSimulator();
    }

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

    // The given event to this method is already a PoMax event. This method will check if the event is a SCMax event.
    // A SCMax event is a PO + RF + FR + CO + ST + TC + JT max event.
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

    public float getExpectedValue() {
        return expectedValue;
    }

    public void reset() {
        expectedValue = 1f;
        executionGraph.reset();
    }
}
