package org.mpi_sws.jmc.strategies.estimation.trust.testor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.estimation.EstimationCollector;
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

/**
 * Scheduling strategy for TeStor ({@code testor}) — stochastic enumeration (Algorithm S), the most
 * advanced and accurate estimator in JMC.
 *
 * <p>It <b>extends {@link TrustStrategy}</b> and drives a {@link Testor} that keeps a coupled
 * frontier of up to {@code budget} nodes of the tree of TruSt. Reaching each frontier node's
 * children requires re-running the program guided by that node's graph, so after an event the
 * estimator may request a re-execution ({@link #nextTask} then returns {@code blockExecution()}), and
 * when the main task finishes with the trial unfinished, {@link #updateEvent} raises a
 * re-execution-needed halt. {@link #resumeWithNextOption} advances the frontier across those
 * re-executions until the estimator {@linkplain Testor#isDone() is done}, at which point the trial's
 * estimate is recorded. The mean over trials estimates {@code C(P)}; results save under {@code
 * testor-*}. Budget {@code 1} coincides with Trust estimation (Algorithm T).
 */
public class TestorStrategy extends TrustStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(TestorStrategy.class);

    /** The Algorithm-S (stochastic enumeration) estimator. */
    protected final Testor testor;

    /** Collects each trial's point estimate and computes the final mean. */
    protected final EstimationCollector estimationCollector = new EstimationCollector();

    /** Creates the strategy with a time-based seed, FIFO policy, no debug, the default report path, and budget 2. */
    public TestorStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    /**
     * Creates the strategy with the default frontier budget of 2.
     *
     * @param randomSeed seed for the fallback scheduling policy.
     * @param policy the TruSt fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for results.
     */
    public TestorStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        this(randomSeed, policy, debug, reportPath, 2);
    }

    /**
     * Creates the strategy with the given frontier budget.
     *
     * @param randomSeed seed for the fallback scheduling policy.
     * @param policy the TruSt fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for results.
     * @param budget the TeStor frontier size.
     */
    public TestorStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath, int budget) {
        super(randomSeed, policy, debug, reportPath);
        if (policy == SchedulingPolicy.RANDOM) {
            LOGGER.warn(String.format("Random scheduling policy is %s", SchedulingPolicy.RANDOM.name()));
        }
        this.testor = new Testor(budget);
    }

    /**
     * Prepares an iteration. When TruSt signals completion with an empty stack: if the estimator is
     * done, records the trial and resets; otherwise advances the frontier to the next node via {@link
     * #resumeWithNextOption}.
     *
     * @param iteration the iteration index.
     * @param report the model-checker report.
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
        try {
            super.initIteration(iteration, report);
        } catch (HaltCheckerException e) {
            if (e.isOkay() && algoInstance.isStackEmpty() && testor.isDone()) {
                recordEstimation();
                algoInstance.clear();
                testor.reset();
            } else if (e.isOkay() && algoInstance.isStackEmpty()) {
                resumeWithNextOption(iteration, report);
            } else {
                LOGGER.error("HaltExecutionException in initIteration: {}", e.getMessage());
                throw HaltExecutionException.ok();
            }
        } finally {
            testor.resetReExecutionFlag();
        }
    }

    /**
     * Advances the estimator's frontier to the next node and starts a guided re-execution toward it;
     * if the estimator becomes done in the process, records the trial and resets instead.
     *
     * @param iteration the iteration index.
     * @param report the model-checker report.
     */
    private void resumeWithNextOption(int iteration, JmcModelCheckerReport report) {
        while (!testor.isDone()) {
            try {
                testor.updateStack(algoInstance);
                algoInstance.initIteration(iteration, report);
                return;
            } catch (HaltCheckerException e) {
                LOGGER.debug(e.getMessage());
            }
        }
        recordEstimation();
        algoInstance.clear();
        testor.reset();
    }

    /** Records the finished trial's estimate into the collector. */
    @Override
    public void recordEstimation() {
        estimationCollector.record(testor.getRealExpectedValue());
    }

    /**
     * Resets per-iteration state (without a consistency check, since exploration is partial).
     *
     * @param iteration the iteration index.
     */
    @Override
    public void resetIteration(int iteration) {
        resetIteration(iteration, false);
    }

    /**
     * Runs TruSt for this event, then (unless re-executing) expands the frontier with the estimator;
     * when the main task finishes while the trial is still unfinished, raises a re-execution-needed
     * halt so the checker starts the next guided run.
     *
     * @param event the runtime event.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt (including re-execution-needed).
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        if (!testor.isReExecutionNeeded()) {
            testor.updateTree(algoInstance);
        }
        if (event.getTaskId() == 1L && event.getType() == JmcRuntimeEvent.Type.FINISH_EVENT) {
            if (!testor.isDone()) {
                throw HaltExecutionException.reexecutionNeeded();
            }
        }
    }

    /**
     * Returns the next scheduling choice, or {@code blockExecution()} to trigger the guided
     * re-execution the estimator requested.
     *
     * @return the next scheduling choice.
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        if (testor.isReExecutionNeeded()) {
            LOGGER.debug("Re-execution needed, returning null to trigger re-execution");
            return SchedulingChoice.blockExecution();
        }
        return super.nextTask();
    }

    /**
     * Saves the collected estimates at shutdown.
     *
     * @param report the model-checker report.
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        saveResults();
    }

    /** Writes the per-trial and final estimates under {@code testor-*}. */
    protected void saveResults() {
        estimationCollector.save(
                "build/test-results/jmc-report/",
                "testor-estimation-result.txt",
                "testor-final-result.txt");
    }
}
