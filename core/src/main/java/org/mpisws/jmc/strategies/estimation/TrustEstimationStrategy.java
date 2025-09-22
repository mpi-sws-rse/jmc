package org.mpisws.jmc.strategies.estimation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.HaltCheckerException;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

public class TrustEstimationStrategy extends TrustStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(TrustEstimationStrategy.class);

    private final TrustEstimator tEst;

    private final StringBuilder estimatorCollector = new StringBuilder();

    public TrustEstimationStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    public TrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(randomSeed, policy, debug, reportPath);
        tEst = new TrustEstimator();
    }

    /**
     * @param iteration the number of the iteration.
     * @param report
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
        try {
            super.initIteration(iteration, report);
            tEst.resetReExecutionFlag();
        } catch (HaltCheckerException e) {
            LOGGER.error("HaltExecutionException in initIteration: {}", e.getMessage());
            throw HaltExecutionException.ok();
        }
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
        }
    }

    /**
     * @return
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        if (tEst.isReExecutionNeeded()) {
            LOGGER.info("Re-execution needed, throwing HaltExecutionException");
            return SchedulingChoice.blockExecution();
        }
        return super.nextTask();
    }
}
