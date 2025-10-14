package org.mpisws.jmc.strategies.estimation.dag.absDag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.estimation.dag.DagEstimator;
import org.mpisws.jmc.strategies.trust.Event;
import org.mpisws.jmc.strategies.trust.EventUtils;

import java.util.List;
import java.util.Set;

public class AbsDagEstimator extends DagEstimator {

    private static final Logger LOGGER = LogManager.getLogger(AbsDagEstimator.class);

    /**
     * @param events
     * @param activeTasks
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Override
    public void updateEvent(List<Event> events, Set<Long> activeTasks) throws HaltTaskException, HaltExecutionException {
        if (!events.isEmpty() && activeTasks.size() != 0) {
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
            if (activeTasks.size() - 1 > 0) {
                updateEstimation(e, activeTasks);
            }
        }
    }

    /**
     * @param e
     * @param activeTasks
     */
    @Override
    protected void updateEstimation(Event e, Set<Long> activeTasks) {
        int in = 1;
        int out = activeTasks.size() - 1;
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
