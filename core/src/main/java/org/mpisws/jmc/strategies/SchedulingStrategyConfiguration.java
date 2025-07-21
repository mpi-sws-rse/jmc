package org.mpisws.jmc.strategies;

import org.mpisws.jmc.strategies.trust.TrustStrategy;

/**
 * Configuration class for scheduling strategies.
 *
 * <p>This class encapsulates the configuration parameters for scheduling strategies, including
 * seed, trust scheduling policy, report path, and debug mode.
 *
 * <p>It provides a builder pattern for constructing instances of the configuration, allowing for
 * flexible and readable configuration of scheduling strategies.
 */
public class SchedulingStrategyConfiguration {
    private Long seed;
    private TrustStrategy.SchedulingPolicy trustSchedulingPolicy;
    private String reportPath;
    private boolean debug;

    private SchedulingStrategyConfiguration() {}

    public Long getSeed() {
        return seed;
    }

    public String getReportPath() {
        return reportPath;
    }

    public boolean getDebug() {
        return debug;
    }

    public TrustStrategy.SchedulingPolicy getTrustSchedulingPolicy() {
        return trustSchedulingPolicy;
    }

    public static class Builder {
        private Long seed;
        private TrustStrategy.SchedulingPolicy trustSchedulingPolicy;
        private String reportPath;
        private boolean debug;

        public Builder() {
            this.seed = null;
            this.trustSchedulingPolicy = TrustStrategy.SchedulingPolicy.RANDOM;
            this.reportPath = "build/test-results/jmc-report";
            this.debug = false;
        }

        public Builder trustSchedulingPolicy(TrustStrategy.SchedulingPolicy trustSchedulingPolicy) {
            this.trustSchedulingPolicy = trustSchedulingPolicy;
            return this;
        }

        public Builder reportPath(String reportPath) {
            this.reportPath = reportPath;
            return this;
        }

        public Builder debug() {
            this.debug = true;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public SchedulingStrategyConfiguration build() {
            SchedulingStrategyConfiguration config = new SchedulingStrategyConfiguration();
            config.seed = this.seed;
            config.trustSchedulingPolicy = this.trustSchedulingPolicy;
            config.reportPath = this.reportPath;
            config.debug = this.debug;
            return config;
        }
    }

    @FunctionalInterface
    public interface SchedulingStrategyConstructor {
        SchedulingStrategy create(SchedulingStrategyConfiguration config);
    }
}
