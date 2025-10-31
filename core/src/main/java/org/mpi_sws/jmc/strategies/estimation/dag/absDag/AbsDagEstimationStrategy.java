package org.mpi_sws.jmc.strategies.estimation.dag.absDag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy;
import org.mpi_sws.jmc.strategies.estimation.MetaGraphEstimator;

import org.mpi_sws.jmc.strategies.trust.Event;
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Paths;
import java.util.List;

public class AbsDagEstimationStrategy extends RandomSchedulingStrategy implements EstimationStrategy {

    private static final Logger LOGGER = LogManager.getLogger(AbsDagEstimationStrategy.class);

    private final MetaGraphEstimator est;

    private final StringBuilder estimatorCollector = new StringBuilder();

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public AbsDagEstimationStrategy(Long seed) {
        // TODO : Fix the hard coded path
        super(seed, "build/test-results/jmc-report");
        est = new AbsDagEstimator();
    }

    public AbsDagEstimationStrategy(Long seed, MetaGraphEstimator est) {
        // TODO : Fix the hard coded path
        super(seed, "build/test-results/jmc-report");
        this.est = est;
    }

    /**
     * @param event
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        List<Event> events = compileRuntimeEvent(event);
        est.updateEvent(events, getActiveTasks());
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
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        // TODO : Fix the hard coded path
        saveResults();
    }

    protected void saveResults() {
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "AbsDagEstimateResult.txt").toString(), estimatorCollector.toString());
    }

    public StringBuilder getEstimatorCollector() {
        return estimatorCollector;
    }
}
