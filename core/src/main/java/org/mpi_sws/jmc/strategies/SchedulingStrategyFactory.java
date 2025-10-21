package org.mpi_sws.jmc.strategies;

import org.mpi_sws.jmc.strategies.estimation.dag.DagEstimationStrategy;
import org.mpi_sws.jmc.strategies.estimation.dag.absDag.AbsDagEstimationStrategy;
import org.mpi_sws.jmc.strategies.estimation.dag.fjDag.FjDagEstimationStrategy;
import org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimationStrategy;
import org.mpi_sws.jmc.strategies.estimation.trust.wgTrust.WgTrustEstimationStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

import java.util.HashSet;
import java.util.Set;

/**
 * Factory class for creating scheduling strategies.
 */
public class SchedulingStrategyFactory {

    // Set of valid strategies
    private static final Set<String> validStrategies = new HashSet<>();

    static {
        validStrategies.add("random");
        validStrategies.add("trust");
        validStrategies.add("dag-estimation");
        validStrategies.add("abs-dag-estimation");
        validStrategies.add("fj-dag-estimation");
        validStrategies.add("trust-estimation");
        validStrategies.add("wg-trust-estimation");
    }

    /**
     * Creates a new scheduling strategy.
     *
     * @param name the name of the strategy
     * @param config the configuration for the strategy
     * @return the scheduling strategy
     */
    public static SchedulingStrategy createSchedulingStrategy(
            String name, SchedulingStrategyConfiguration config)
            throws JmcInvalidStrategyException {
        if (!isValidStrategy(name)) {
            throw new JmcInvalidStrategyException("Invalid strategy: " + name);
        }
        if (name.equals("random")) {
            return new RandomSchedulingStrategy(config.getSeed(), config.getReportPath());
        } else if (name.equals("trust")) {
            return new TrustStrategy(
                    config.getSeed(),
                    config.getTrustSchedulingPolicy(),
                    config.getDebug(),
                    config.getReportPath());
        } else if (name.equals("dag-estimation")) {
            return new DagEstimationStrategy(config.getSeed());
        } else if (name.equals("abs-dag-estimation")) {
            return new AbsDagEstimationStrategy(config.getSeed());
        } else if (name.equals("fj-dag-estimation")) {
            return new FjDagEstimationStrategy(config.getSeed());
        } else if (name.equals("trust-estimation")) {
            return new TrustEstimationStrategy(
                    config.getSeed(),
                    config.getTrustSchedulingPolicy(),
                    config.getDebug(),
                    config.getReportPath());
        } else if (name.equals("wg-trust-estimation")) {
            return new WgTrustEstimationStrategy(
                    config.getSeed(),
                    config.getTrustSchedulingPolicy(),
                    config.getDebug(),
                    config.getReportPath()
            );
        }
        return null;
    }

    /**
     * Checks if a strategy is valid.
     *
     * @param name the name of the strategy
     * @return true if the strategy is valid, false otherwise
     */
    public static boolean isValidStrategy(String name) {
        return validStrategies.contains(name);
    }
}
