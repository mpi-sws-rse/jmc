package org.mpisws.jmc.strategies.estimation.absDag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.strategies.estimation.dag.DagEstimationStrategy;

import org.mpisws.jmc.util.FileUtil;

import java.nio.file.Paths;

public class AbsDagEstimationStrategy extends DagEstimationStrategy {

    private static final Logger LOGGER = LogManager.getLogger(AbsDagEstimationStrategy.class);

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public AbsDagEstimationStrategy(Long seed) {
        super(seed, new AbsDagEstimator());
    }

    /**
     *
     */
    @Override
    protected void saveResults() {
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "AbsDagEstimateResult.txt").toString(), getEstimatorCollector().toString());
    }
}
