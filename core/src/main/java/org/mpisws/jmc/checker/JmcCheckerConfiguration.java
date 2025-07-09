package org.mpisws.jmc.checker;

import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.checker.exceptions.JmcInvalidConfigurationException;
import org.mpisws.jmc.runtime.JmcRuntimeConfiguration;
import org.mpisws.jmc.strategies.JmcInvalidStrategyException;
import org.mpisws.jmc.strategies.SchedulingStrategy;
import org.mpisws.jmc.strategies.SchedulingStrategyConfiguration;
import org.mpisws.jmc.strategies.SchedulingStrategyFactory;

import java.time.Duration;

/**
 * Configuration for the JMC checker.
 *
 * <p>This class encapsulates the configuration parameters for running the JMC checker, including
 * the number of iterations, strategy type, debug mode, report path, seed, and timeout.
 *
 * <p>Use the {@link JmcCheckerConfiguration.Builder} to create a configuration instance.
 */
public class JmcCheckerConfiguration {
    private Integer numIterations;

    private String strategyType;
    private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

    private boolean debug;

    private Long seed;

    private String reportPath;

    private Duration timeout;

    private JmcCheckerConfiguration() {}

    /**
     * Returns the number of iterations to run the checker.
     *
     * @return the number of iterations
     */
    public Integer getNumIterations() {
        return numIterations;
    }

    /**
     * Returns the path where the report will be saved.
     *
     * @return the report path as a string
     */
    public String getReportPath() {
        return reportPath;
    }

    /**
     * Returns the type of scheduling strategy to be used.
     *
     * @return the strategy type as a string
     */
    public String getStrategyType() {
        return strategyType;
    }

    /**
     * Returns the debug mode status.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean getDebug() {
        return debug;
    }

    /**
     * Returns the seed for the checker.
     *
     * @return the seed, or null if no seed is set
     */
    public Long getSeed() {
        return seed;
    }

    /**
     * Sets the seed for the checker.
     *
     * @param seed the seed to set.
     */
    public void setSeed(Long seed) {
        this.seed = seed;
    }

    /**
     * Returns the timeout duration for the checker.
     *
     * @return the timeout duration, or null if no timeout is set
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Converts this configuration to a runtime configuration.
     *
     * @return a {@link JmcRuntimeConfiguration} based on this configuration
     * @throws JmcInvalidStrategyException if the strategy type is invalid or the strategy cannot be
     *     created
     */
    public JmcRuntimeConfiguration toRuntimeConfiguration() throws JmcInvalidStrategyException {
        SchedulingStrategy strategy;
        SchedulingStrategyConfiguration.Builder strategyConfigurationBuilder =
                new SchedulingStrategyConfiguration.Builder().seed(seed);
        if (debug) {
            strategyConfigurationBuilder.debug();
            strategyConfigurationBuilder.reportPath(reportPath);
        }
        if (strategyConstructor != null) {
            strategy = strategyConstructor.create(strategyConfigurationBuilder.build());
        } else {
            strategy =
                    SchedulingStrategyFactory.createSchedulingStrategy(
                            strategyType, strategyConfigurationBuilder.build());
        }
        if (strategy == null) {
            throw new JmcInvalidStrategyException("Strategy is null");
        }
        return new JmcRuntimeConfiguration.Builder()
                .strategy(strategy)
                .debug(debug)
                .reportPath(reportPath)
                .build();
    }

    /**
     * Creates a JmcCheckerConfiguration from the given annotation.
     *
     * @param annotation the JmcCheckConfiguration annotation
     * @return a JmcCheckerConfiguration instance
     * @throws JmcCheckerException if the configuration is invalid
     */
    public static JmcCheckerConfiguration fromAnnotation(JmcCheckConfiguration annotation)
            throws JmcCheckerException {
        if (!SchedulingStrategyFactory.isValidStrategy(annotation.strategy())) {
            throw new JmcInvalidStrategyException("Invalid strategy: " + annotation.strategy());
        }
        return new Builder()
                .numIterations(annotation.numIterations())
                .strategyType(annotation.strategy())
                .debug(annotation.debug())
                .reportPath(annotation.reportPath())
                .seed(annotation.seed())
                .build();
    }

    /** Builder for JmcCheckerConfiguration */
    public static class Builder {
        private Integer numIterations;

        private String strategyType;
        private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

        private boolean debug;

        private String reportPath;

        private Duration timeout;

        private Long seed;

        public Builder() {
            this.numIterations = 0;
            this.strategyType = "random";
            this.debug = false;
            this.reportPath = "build/test-results/jmc-report";
            this.seed = System.nanoTime();
            this.timeout = null;
        }

        public Builder numIterations(Integer numIterations) {
            this.numIterations = numIterations;
            return this;
        }

        public Builder strategyType(String strategyType) {
            this.strategyType = strategyType;
            return this;
        }

        public Builder strategyConstructor(
                SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor) {
            this.strategyConstructor = strategyConstructor;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder reportPath(String bugsPath) {
            this.reportPath = bugsPath;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public JmcCheckerConfiguration build() throws JmcInvalidConfigurationException {
            if (numIterations == 0 && timeout == null) {
                throw new JmcInvalidConfigurationException(
                        "Either numIterations or timeout must be set");
            }
            JmcCheckerConfiguration config = new JmcCheckerConfiguration();
            config.numIterations = numIterations;
            config.strategyType = strategyType;
            config.strategyConstructor = strategyConstructor;
            config.debug = debug;
            config.reportPath = reportPath;
            config.seed = seed;
            config.timeout = timeout;
            return config;
        }
    }
}
