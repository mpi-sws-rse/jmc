package org.mpisws.jmc.strategies.estimation.dag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.estimation.MetaGraphEstimator;
import org.mpisws.jmc.strategies.trust.*;

import java.util.List;

public class DagEstimator implements MetaGraphEstimator {

    private static final Logger LOGGER = LogManager.getLogger(DagEstimator.class);

    protected final ExecutionGraphSimulator executionGraph;

    protected float expectedValue = 1f;

    public DagEstimator() {
        this.executionGraph = new ExecutionGraphSimulator();
    }

    public void updateEvent(List<Event> events, int activeThreadSize) throws HaltTaskException, HaltExecutionException {

        if (!events.isEmpty() && activeThreadSize != 0) {
            // The lock acquisition and release events, will be compiled into a pair of ReadEx and WriteEx events
            for (Event e : events) {
                LOGGER.debug("Received event: {}", e);
                executionGraph.updateEvent(e);
            }

            // Update the estimation based on the last event
            Event e = events.get(events.size() - 1);
            updateEstimation(e, activeThreadSize);
        }
    }

    protected void updateEstimation(Event e, int activeThreadSize) {
        int in = 1;
        int out = activeThreadSize;
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
