package org.mpi_sws.jmc.strategies.estimation.dag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;
import org.mpi_sws.jmc.strategies.estimation.EstimationCollector;
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy;
import org.mpi_sws.jmc.strategies.estimation.MetaGraphEstimator;
import org.mpi_sws.jmc.strategies.trust.Event;
import org.mpi_sws.jmc.strategies.trust.EventFactory;
import org.mpi_sws.jmc.strategies.trust.LocationStore;

import java.util.List;

/**
 * Scheduling strategy for the PeStor estimator ({@code pestor}) — Pitt's DAG estimator (Algorithm
 * P).
 *
 * <p>It <b>extends {@link RandomSchedulingStrategy}</b> so the uniform random scheduler drives a
 * random walk over {@code T(P)}; on every event it feeds the corresponding trust events to a {@link
 * DagEstimator}, which maintains the running {@code ∏ out/in} estimate. Each iteration is one
 * independent trial: its point estimate is recorded on reset, and the {@link EstimationCollector}'s
 * mean over trials is the final estimate. Results save under {@code pestor-*}.
 */
public class DagEstimationStrategy extends RandomSchedulingStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(DagEstimationStrategy.class);

    /** The DAG estimator maintaining the running estimate for the current walk. */
    private final MetaGraphEstimator est;

    /** Collects each trial's point estimate and computes the final mean. */
    private final EstimationCollector estimationCollector = new EstimationCollector();


    /**
     * Creates the strategy with a default {@link DagEstimator}.
     *
     * @param seed the seed for the random scheduler.
     */
    public DagEstimationStrategy(Long seed) {
        // TODO : Fix the hard coded path
        super(seed, "build/test-results/jmc-report");
        est = new DagEstimator();
    }

    /**
     * Creates the strategy with a supplied estimator (used to inject alternatives, e.g. in tests).
     *
     * @param seed the seed for the random scheduler.
     * @param est the DAG estimator to drive.
     */
    public DagEstimationStrategy(Long seed, MetaGraphEstimator est) {
        // TODO : Fix the hard coded path
        super(seed, "build/test-results/jmc-report");
        this.est = est;
    }

    /**
     * Tracks the event with the random scheduler and forwards it to the estimator.
     *
     * @param event the runtime event.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        List<Event> events = compileRuntimeEvent(event);
        est.updateEvent(events, getActiveTasks());
    }

    /**
     * Ends the current trial: records its point estimate and resets the estimator for the next
     * iteration.
     *
     * @param iteration the iteration index.
     */
    @Override
    public void resetIteration(int iteration) {
        super.resetIteration(iteration);
        LOGGER.debug("Finished iteration {} with expected value: {}", iteration, est.getExpectedValue());
        recordEstimation();
        est.reset();
    }

    /** Records the current walk's estimate into the collector. */
    @Override
    public void recordEstimation() {
        estimationCollector.record(est.getExpectedValue());
    }

    /**
     * Saves the collected estimates at shutdown.
     *
     * @param report the model-checker report.
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        // TODO : Fix the hard coded path
        saveResults();
    }

    /** Writes the per-trial and final estimates to {@code pestor-result.txt} / {@code pestor-final-result.txt}. */
    protected void saveResults() {
        estimationCollector.save(
                "build/test-results/jmc-report/", "pestor-result.txt", "pestor-final-result.txt");
    }
}
