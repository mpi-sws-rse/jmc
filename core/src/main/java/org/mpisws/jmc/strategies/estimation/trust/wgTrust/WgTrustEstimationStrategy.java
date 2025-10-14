package org.mpisws.jmc.strategies.estimation.trust.wgTrust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.strategies.estimation.trust.TrustEstimationStrategy;
import org.mpisws.jmc.util.FileUtil;

import java.nio.file.Paths;

public class WgTrustEstimationStrategy extends TrustEstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(WgTrustEstimationStrategy.class);

    public WgTrustEstimationStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    public WgTrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(randomSeed, policy, debug, reportPath, new WgTrustEstimator());
    }

    @Override
    public void teardown() {
        super.teardown();
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "WgTrustEstimateResult.txt").toString(), estimatorCollector.toString());
    }
}
