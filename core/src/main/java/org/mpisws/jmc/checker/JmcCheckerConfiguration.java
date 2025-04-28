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

public class JmcCheckerConfiguration {
    private Integer numIterations;

    private String strategyType;
    private SchedulingStrategy customStrategy;
    private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

    private boolean debug;

    private Long seed;

    private String reportPath;

    private Duration timeout;

    private JmcCheckerConfiguration() {}

    public Integer getNumIterations() {
        return numIterations;
    }

    public String getReportPath() {
        return reportPath;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public SchedulingStrategy getCustomStrategy() {
        return customStrategy;
    }

    public boolean getDebug() {
        return debug;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isCustomStrategy() {
        return customStrategy != null;
    }

    public JmcRuntimeConfiguration toRuntimeConfiguration() throws JmcInvalidStrategyException {
        SchedulingStrategy strategy;
        if (customStrategy != null) {
            strategy = customStrategy;
        } else {
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
        private SchedulingStrategy customStrategy;
        private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

        private boolean debug;

        private String reportPath;

        private Duration timeout;

        private Long seed;

        public Builder() {
            this.numIterations = 0;
            this.strategyType = "random";
            this.customStrategy = null;
            this.debug = false;
            this.reportPath = "build/test-results/jmc-report";
            this.seed = System.nanoTime();
            this.timeout = Duration.ofMinutes(10);
        }

        public Builder numIterations(Integer numIterations) {
            this.numIterations = numIterations;
            return this;
        }

        public Builder strategyType(String strategyType) {
            this.strategyType = strategyType;
            return this;
        }

        public Builder customStrategy(SchedulingStrategy customStrategy) {
            this.customStrategy = customStrategy;
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
            config.customStrategy = customStrategy;
            config.strategyConstructor = strategyConstructor;
            config.debug = debug;
            config.reportPath = reportPath;
            config.seed = seed;
            config.timeout = timeout;
            return config;
        }
    }
}
