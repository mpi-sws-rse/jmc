package org.mpi_sws.jmc.strategies.estimation.trust.wgTrust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimationStrategy;
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WgTrustEstimationStrategy extends TrustEstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(WgTrustEstimationStrategy.class);

    public WgTrustEstimationStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    public WgTrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(randomSeed, policy, debug, reportPath, new WgTrustEstimator());
    }

    /**
     *
     */
    @Override
    protected void saveResults() {
        estimationCollector.save(
                "build/test-results/jmc-report/",
                "wg-trust-estimation-result.txt",
                "wg-trust-final-result.txt");
        final Path path1 = Paths.get("build/test-results/jmc-report/", "wg-trust-branching-result.txt");
        FileUtil.unsafeStoreToFile(
                path1.toString(), branchingCollector.toString());
        LOGGER.info("The branching information per each iteration can be found in the file: " +
                "{}", path1.toString());
    }
}
