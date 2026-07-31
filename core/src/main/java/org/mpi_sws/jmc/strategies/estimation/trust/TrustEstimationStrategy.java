package org.mpi_sws.jmc.strategies.estimation.trust;

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
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Scheduling strategy for Trust estimation ({@code trust-estimation}) — Knuth's estimator over the
 * tree of TruSt (Algorithm T).
 *
 * <p>It <b>extends {@link TrustStrategy}</b>, turning TruSt's systematic DFS into a single random
 * walk: after each event a {@link TrustEstimator} samples among the revisits TruSt generated and
 * multiplies the branching factor into the estimate. Because a chosen revisit need not follow DFS
 * order, the estimator sets a re-execution flag; {@link #nextTask} then returns {@code
 * blockExecution()} so the checker re-runs the program guided by the chosen revisit's graph. One
 * root-to-leaf walk (spanning several iterations) is one trial; the {@link EstimationCollector}'s
 * mean over trials is the final estimate. Results save under {@code trust-*}.
 */
public class TrustEstimationStrategy extends TrustStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(TrustEstimationStrategy.class);

    /** The Algorithm-T estimator (or a subclass, e.g. the weighted variant). */
    protected final TrustEstimator tEst;

    /** Collects each trial's point estimate and computes the final mean. */
    protected final EstimationCollector estimationCollector = new EstimationCollector();

    /** Accumulates the per-trial tree logs, written out at teardown. */
    protected final StringBuilder branchingCollector = new StringBuilder();

    /** Counts completed trials (used to label the tree logs). */
    private int branchCounter = 0;

    /** Creates the strategy with a time-based seed, FIFO policy, no debug, and the default report path. */
    public TrustEstimationStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    /**
     * Creates the strategy with a default {@link TrustEstimator}.
     *
     * @param randomSeed seed for the fallback scheduling policy.
     * @param policy the TruSt fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for results.
     */
    public TrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        this(randomSeed, policy, debug, reportPath, new TrustEstimator());
    }

    /**
     * Creates the strategy with a supplied estimator (used to inject the weighted variant).
     *
     * @param randomSeed seed for the fallback scheduling policy.
     * @param policy the TruSt fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for results.
     * @param tEst the tree estimator to drive.
     */
    public TrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath, TrustEstimator tEst) {
        super(randomSeed, policy, debug, reportPath);
        if (policy == SchedulingPolicy.RANDOM) {
            LOGGER.warn(String.format("Random scheduling policy is %s", SchedulingPolicy.RANDOM.name()));
        }
        this.tEst = tEst;
    }

    /**
     * Prepares an iteration: sets up TruSt guiding for the chosen revisit; when the walk reaches a
     * leaf (TruSt signals completion with an empty stack), records the trial's estimate and resets
     * the estimator.
     *
     * @param iteration the iteration index.
     * @param report the model-checker report.
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
        try {
            super.initIteration(iteration, report);
        } catch (HaltCheckerException e) {
            if (e.isOkay() && algoInstance.isStackEmpty()) {
                LOGGER.debug("HaltCheckerException in initIteration: {}, clearing algoInstance", e.getMessage());
                algoInstance.clear();
                recordEstimation();
                tEst.reset();
            } else {
                LOGGER.error("HaltExecutionException in initIteration: {}", e.getMessage());
                throw HaltExecutionException.ok();
            }
        } finally {
            tEst.resetReExecutionFlag();
        }
    }

    /** Records the completed walk's estimate and appends its tree log. */
    @Override
    public void recordEstimation() {
        estimationCollector.record(tEst.getExpectedValue());
        branchCounter++;
        branchingCollector.append("$Iteration_").append(branchCounter).append(System.lineSeparator());
        branchingCollector.append(tEst.getTreeLogger().toString()).append(System.lineSeparator());
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
     * Runs TruSt for this event, then (unless re-executing) samples the branching with the
     * estimator; asserts the exploration stack does not exceed one item after sampling.
     *
     * @param event the runtime event.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        if (!tEst.isReExecutionNeeded()) {
            tEst.updateTree(algoInstance);
            if (!algoInstance.isStackEmpty() && algoInstance.getExplorationStack().size() > 1) {
                throw HaltExecutionException.error("Exploration stack size exceeded 1");
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
        if (tEst.isReExecutionNeeded()) {
            LOGGER.debug("Re-execution needed, throwing HaltExecutionException");
            return SchedulingChoice.blockExecution();
        }
        return super.nextTask();
    }

    /**
     * Saves the collected estimates and tree logs at shutdown.
     *
     * @param report the model-checker report.
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        saveResults();
    }

    /** Writes the per-trial estimates, final mean, and per-trial tree logs under {@code trust-*}. */
    protected void saveResults() {
        estimationCollector.save(
                "build/test-results/jmc-report/",
                "trust-estimation-result.txt",
                "trust-final-result.txt");
        final Path path1 = Paths.get("build/test-results/jmc-report/", "trust-branching-result.txt");
        FileUtil.unsafeStoreToFile(
                path1.toString(), branchingCollector.toString());
        LOGGER.info("The branching information per each iteration can be found in the file: " +
                "{}", path1.toString());
    }
}
