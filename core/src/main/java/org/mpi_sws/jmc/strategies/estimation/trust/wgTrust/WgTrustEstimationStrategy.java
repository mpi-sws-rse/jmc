package org.mpi_sws.jmc.strategies.estimation.trust.wgTrust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimationStrategy;
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Scheduling strategy for weighted Trust estimation ({@code wg-trust-estimation}).
 *
 * <p>Identical to {@link TrustEstimationStrategy} except it injects a {@link WgTrustEstimator}, which
 * samples the tree of TruSt with weighted (rather than uniform) child selection. Results save under
 * {@code wg-trust-*}.
 *
 * <p><b>Note:</b> as documented on {@link WgTrustEstimator}, the weighted estimator is currently a
 * known <em>biased</em> heuristic (weighting without an importance-sampling correction); prefer
 * {@link TrustEstimationStrategy} or {@code testor} for an unbiased estimate.
 */
public class WgTrustEstimationStrategy extends TrustEstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(WgTrustEstimationStrategy.class);

    /** Creates the strategy with a time-based seed, FIFO policy, no debug, and the default report path. */
    public WgTrustEstimationStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    /**
     * Creates the strategy, injecting a {@link WgTrustEstimator} into the base Trust-estimation strategy.
     *
     * @param randomSeed seed for the fallback scheduling policy.
     * @param policy the TruSt fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for results.
     */
    public WgTrustEstimationStrategy(Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(randomSeed, policy, debug, reportPath, new WgTrustEstimator());
    }

    /** Writes the per-trial estimates, final mean, and per-trial tree logs under {@code wg-trust-*}. */
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
