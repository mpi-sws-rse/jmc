package org.mpisws.jmc.strategies.estimation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.Event;
import org.mpisws.jmc.strategies.trust.EventFactory;

import java.util.List;

public class EstimationStrategy extends RandomSchedulingStrategy {

    private final Logger LOGGER = LogManager.getLogger(EstimationStrategy.class);

    private final Estimator est;

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public EstimationStrategy(Long seed) {
        super(seed);
        est = new Estimator();
    }

    /**
     * @param event
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Override
    public void updateEvent(RuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        List<Event> events = EventFactory.fromRuntimeEvent(event);
        est.updateEvent(events, getActiveTasks().size());
    }

    /**
     * @param iteration
     */
    @Override
    public void resetIteration(int iteration) {
        super.resetIteration(iteration);
        LOGGER.info("Finished iteration {} with expected value: {}", iteration, est.getExpectedValue());
        est.reset();
    }
}
