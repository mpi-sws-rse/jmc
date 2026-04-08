package org.mpi_sws.jmc.strategies.estimation.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Paths;

public class TrustEstimationStrategy extends TrustStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(TrustEstimationStrategy.class);

    protected final TrustEstimator tEst;

    protected final StringBuilder estimatorCollector = new StringBuilder();

    protected final StringBuilder branchingCollector = new StringBuilder();

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
                estimatorCollector.append(tEst.getExpectedValue()).append(System.lineSeparator());
                branchingCollector.append(tEst.getTreeLogger().toString()).append(System.lineSeparator());
                branchingCollector.append("$Iteration_").append(iteration).append(System.lineSeparator());
                tEst.reset();
            } else {
                LOGGER.error("HaltExecutionException in initIteration: {}", e.getMessage());
                throw HaltExecutionException.ok();
            }
        } finally {
            tEst.resetReExecutionFlag();
        }
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
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "TrustEstimateResult.txt").toString(), estimatorCollector.toString());
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "TrustBranchingResult.txt").toString(), branchingCollector.toString());
    }
}
