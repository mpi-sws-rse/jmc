package org.mpisws.jmc.checker;

import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.runtime.JmcRuntimeConfiguration;
import org.mpisws.jmc.strategies.InvalidStrategyException;
import org.mpisws.jmc.strategies.SchedulingStrategy;
import org.mpisws.jmc.strategies.SchedulingStrategyConfiguration;
import org.mpisws.jmc.strategies.SchedulingStrategyFactory;

public class JmcCheckerConfiguration {
    private Integer numIterations;

    private String strategyType;
    private SchedulingStrategy customStrategy;
    private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

    private boolean debug;

    private Long seed;

    private String reportPath;

    private JmcCheckerConfiguration() {
    }

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

    public boolean isCustomStrategy() {
        return customStrategy != null;
    }

    public JmcRuntimeConfiguration toRuntimeConfiguration() throws InvalidStrategyException {
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
            throw new InvalidStrategyException("Strategy is null");
        }
        return new JmcRuntimeConfiguration.Builder()
                .strategy(strategy)
                .debug(debug)
                .reportPath(reportPath)
                .build();
    }

    public static JmcCheckerConfiguration fromAnnotation(JmcCheckConfiguration annotation)
            throws InvalidStrategyException {
        if (!SchedulingStrategyFactory.isValidStrategy(annotation.strategy())) {
            throw new InvalidStrategyException("Invalid strategy: " + annotation.strategy());
        }
        return new Builder()
                .numIterations(annotation.numIterations())
                .strategyType(annotation.strategy())
                .debug(annotation.debug())
                .reportPath(annotation.reportPath())
                .seed(annotation.seed())
                .build();
    }

    public static class Builder {
        private Integer numIterations;

        private String strategyType;
        private SchedulingStrategy customStrategy;
        private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

        private boolean debug;

        private String reportPath;

        private Long seed;

        public Builder() {
            this.numIterations = 1;
            this.strategyType = "random";
            this.customStrategy = null;
            this.debug = false;
            this.reportPath = "build/test-results/jmc-report";
            this.seed = System.nanoTime();
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

        public Builder strategyConstructor(SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor) {
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

        public JmcCheckerConfiguration build() {
            JmcCheckerConfiguration config = new JmcCheckerConfiguration();
            config.numIterations = numIterations;
            config.strategyType = strategyType;
            config.customStrategy = customStrategy;
            config.strategyConstructor = strategyConstructor;
            config.debug = debug;
            config.reportPath = reportPath;
            config.seed = seed;
            return config;
        }
    }
}
