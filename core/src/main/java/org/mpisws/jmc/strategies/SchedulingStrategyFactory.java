package org.mpisws.jmc.strategies;

import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.util.HashSet;
import java.util.Set;

/** Factory class for creating scheduling strategies. */
public class SchedulingStrategyFactory {

    // Set of valid strategies
    private static final Set<String> validStrategies = new HashSet<>();

    static {
        validStrategies.add("random");
        validStrategies.add("trust");
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
