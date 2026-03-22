package org.mpi_sws.jmc.strategies.estimation.trust.testor;

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

public class TestorStrategy extends TrustStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(TestorStrategy.class);
    protected final Testor testor;
    protected final StringBuilder estimatorCollector = new StringBuilder();

    public TestorStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    public TestorStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        this(randomSeed, policy, debug, reportPath, 2);
    }

    public TestorStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath, int budget) {
        super(randomSeed, policy, debug, reportPath);
        if (policy == SchedulingPolicy.RANDOM) {
            LOGGER.warn(String.format("Random scheduling policy is %s", SchedulingPolicy.RANDOM.name()));
        }
        this.testor = new Testor(budget);
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
            if (e.isOkay() && algoInstance.isStackEmpty() && testor.isDone()) {
                recordEstimation(iteration);
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
        recordEstimation(iteration);
        algoInstance.clear();
        testor.reset();
    }

    private void recordEstimation(int iteration) {
        estimatorCollector.append(testor.getRealExpectedValue()).append(System.lineSeparator());
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
     * @return
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
     * @param report
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        saveResults();
    }

    protected void saveResults() {
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "TestorEstimateResult.txt").toString(), estimatorCollector.toString());
    }
}
