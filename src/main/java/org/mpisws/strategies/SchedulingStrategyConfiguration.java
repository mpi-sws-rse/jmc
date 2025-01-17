package org.mpisws.strategies;

import org.mpisws.strategies.trust.TrustStrategy;

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
}
