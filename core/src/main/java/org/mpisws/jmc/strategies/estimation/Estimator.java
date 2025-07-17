package org.mpisws.jmc.strategies.estimation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.trust.*;

import java.util.List;

public class Estimator {

    private static final Logger LOGGER = LogManager.getLogger(Estimator.class);

    private final ExecutionGraphSimulator executionGraph;

    private float expectedValue = 1f;

    public Estimator() {
        this.executionGraph = new ExecutionGraphSimulator();
    }

    public void updateEvent(List<Event> events, int activeThreadSize) throws HaltTaskException, HaltExecutionException {

        if (!events.isEmpty() && activeThreadSize != 0) {
            // The lock acquisition and release events, will be compiled into a pair of ReadEx and WriteEx events
            for (Event e : events) {
                LOGGER.info("Received event: {}", e);
                executionGraph.updateEvent(e);
            }

            // Update the estimation based on the last event
            Event e = events.get(events.size() - 1);
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
            LOGGER.info("Expected value: {}", expectedValue);
        }

    }

    // The given event to this method is already a PoMax event. This method will check if the event is a SCMax event.
    // A SCMax event is a PO + RF + FR + CO + ST + TC + JT max event.
    private boolean isScMax(Event e) {
        return executionGraph.isCoMax(e) &&
                executionGraph.isRfMax(e) &&
                executionGraph.isFrMax(e) &&
                executionGraph.isTcMax(e) &&
                executionGraph.isStMax(e) &&
                executionGraph.isJtMax(e);
    }

    private boolean conflict(Event e1, Event e2) {
        if (!EventUtils.isWrite(e1) || !EventUtils.isWrite(e2)) {
            if (EventUtils.isThreadStart(e1)) {
                long startedBy = EventUtils.getStartedBy(e1);
                // We need to check if the START event is PO-MAX regarding the PO-MAX of the starter thread
                return startedBy == e2.getTaskId() || !executionGraph.isStartMaxWithStarter(e1);
            }
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
