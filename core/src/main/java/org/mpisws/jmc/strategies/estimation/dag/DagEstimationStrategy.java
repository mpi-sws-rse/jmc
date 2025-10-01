package org.mpisws.jmc.strategies.estimation.dag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.estimation.EstimationStrategy;
import org.mpisws.jmc.strategies.trust.Event;
import org.mpisws.jmc.strategies.trust.EventFactory;
import org.mpisws.jmc.strategies.trust.LocationStore;
import org.mpisws.jmc.util.FileUtil;

import java.nio.file.Paths;
import java.util.List;

public class DagEstimationStrategy extends RandomSchedulingStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(DagEstimationStrategy.class);

    private final DagEstimator est;

    private final StringBuilder estimatorCollector = new StringBuilder();


    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public DagEstimationStrategy(Long seed) {
        // TODO : Fix the hard coded path
        super(seed, "build/test-results/jmc-report");
        est = new DagEstimator();
    }

    /**
     * @param event
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        List<Event> events = EventFactory.fromRuntimeEvent(event);
        if (event.getType() == JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT) {
            Event e =
                    new Event(
                            event.getTaskId() - 1,
                            LocationStore.ThreadLocation,
                            Event.Type.NOOP);
            e.setAttribute("join-req", true);
            events.add(e);
        }
        est.updateEvent(events, getActiveTasks().size());
    }

    /**
     * @param iteration
     */
    @Override
    public void resetIteration(int iteration) {
        super.resetIteration(iteration);
        LOGGER.debug("Finished iteration {} with expected value: {}", iteration, est.getExpectedValue());
        estimatorCollector.append(est.getExpectedValue()).append(System.lineSeparator());
        est.reset();
    }

    @Override
    public void teardown() {
        super.teardown();
        // TODO : Fix the hard coded path
        saveResults();
    }

    protected void saveResults() {
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "DagEstimateResult.txt").toString(), estimatorCollector.toString());
    }

    public StringBuilder getEstimatorCollector() {
        return estimatorCollector;
    }
}
