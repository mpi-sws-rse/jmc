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

        // The lock acquisition and release events, will be compiled into a pair of ReadEx and WriteEx events
        for (Event e : events) {
            LOGGER.info("Received event: {}", e);
            executionGraph.updateEvent(e);
        }

        // Update the estimation based on the last event
        Event e = events.get(events.size() - 1);
        int in = 1;
        int out = activeThreadSize;
        // TODO :: check if the poMax events are also rf, co, fr max
        List<Event> poMax = executionGraph.getAllPoMaxEvents();
        for (Event poMaxEvent : poMax) {
            if (poMaxEvent.getTaskId() != e.getTaskId()) {
                if (!conflict(poMaxEvent, e)) {
                    in++;
                }
            }
        }

        expectedValue = expectedValue * out / in;
        LOGGER.info("Expected value: {}", expectedValue);

    }

    private boolean conflict(Event e1, Event e2) {
        // TODO :: check the conflict between an starter and influenced thread event
        if (!EventUtils.isWrite(e1) || !EventUtils.isWrite(e2)) {
            if (EventUtils.isThreadStart(e1) && EventUtils.isThreadFinish(e2)) {
                if (e1 == e2) {
                    long startedBy = EventUtils.getStartedBy(e1);
                    return startedBy == e2.getTaskId();
                }
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
