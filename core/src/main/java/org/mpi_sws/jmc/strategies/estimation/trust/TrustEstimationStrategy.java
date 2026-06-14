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

public class TrustEstimationStrategy extends TrustStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(TrustEstimationStrategy.class);

    protected final TrustEstimator tEst;

    protected final EstimationCollector estimationCollector = new EstimationCollector();

    protected final StringBuilder branchingCollector = new StringBuilder();

    private int branchCounter = 0;

    public TrustEstimationStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    public TrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        this(randomSeed, policy, debug, reportPath, new TrustEstimator());
    }

    public TrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath, TrustEstimator tEst) {
        super(randomSeed, policy, debug, reportPath);
        if (policy == SchedulingPolicy.RANDOM) {
            LOGGER.warn(String.format("Random scheduling policy is %s", SchedulingPolicy.RANDOM.name()));
        }
        this.tEst = tEst;
    }

    /**
     * @param iteration the number of the iteration.
     * @param report
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

    @Override
    public void recordEstimation() {
        estimationCollector.record(tEst.getExpectedValue());
        branchCounter++;
        branchingCollector.append("$Iteration_").append(branchCounter).append(System.lineSeparator());
        branchingCollector.append(tEst.getTreeLogger().toString()).append(System.lineSeparator());
    }

    /**
     * @param iteration
     */
    @Override
    public void resetIteration(int iteration) {
        resetIteration(iteration, false);
    }

    /**
     * @param event
     * @throws HaltTaskException
     * @throws HaltExecutionException
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
     * @return
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
     *
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        saveResults();
    }

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
