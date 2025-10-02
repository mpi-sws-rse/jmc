package org.mpisws.jmc.strategies.estimation.absDag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.estimation.dag.DagEstimator;
import org.mpisws.jmc.strategies.trust.Event;
import org.mpisws.jmc.strategies.trust.EventUtils;

import java.util.List;

public class AbsDagEstimator extends DagEstimator {

    private static final Logger LOGGER = LogManager.getLogger(AbsDagEstimator.class);

    /**
     * @param events
     * @param activeThreadSize
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Override
    public void updateEvent(List<Event> events, int activeThreadSize) throws HaltTaskException, HaltExecutionException {
        if (!events.isEmpty() && activeThreadSize != 0) {
            for (Event e : events) {
                LOGGER.debug("Received event: {}", e);
                if (EventUtils.isThreadFinish(e) || EventUtils.isThreadJoin(e) || EventUtils.isJoinRequest(e)) {
                    return;
                }
                executionGraph.updateEvent(e);
            }
            Event e = events.get(events.size() - 1);
            if (EventUtils.isNoop(e)) {
                return;
            }
            if (activeThreadSize - 1 > 0) {
                updateEstimation(e, activeThreadSize - 1);
            }
        }
    }

    /**
     * @param e
     * @param activeThreadSize
     */
    @Override
    protected void updateEstimation(Event e, int activeThreadSize) {
        int in = 1;
        int out = activeThreadSize;
        List<Event> poMax = executionGraph.getAllPoMaxEvents();
        for (Event poMaxEvent : poMax) {
            if (!EventUtils.isNoop(poMaxEvent) &&
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
