package org.mpisws.strategies;

import org.mpisws.strategies.trust.TrustStrategy;

public class SchedulingStrategyConfiguration {
    private Long seed;
    private TrustStrategy.SchedulingPolicy trustSchedulingPolicy;

    private SchedulingStrategyConfiguration() {
    }

    public Long getSeed() {
        return seed;
    }

    public TrustStrategy.SchedulingPolicy getTrustSchedulingPolicy() {
        return trustSchedulingPolicy;
    }

    public static class Builder {
        private Long seed;
        private TrustStrategy.SchedulingPolicy trustSchedulingPolicy;

        public Builder() {
            this.seed = null;
            this.trustSchedulingPolicy = TrustStrategy.SchedulingPolicy.RANDOM;
        }

        public Builder trustSchedulingPolicy(TrustStrategy.SchedulingPolicy trustSchedulingPolicy) {
            this.trustSchedulingPolicy = trustSchedulingPolicy;
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
            return config;
        }
    }
}
