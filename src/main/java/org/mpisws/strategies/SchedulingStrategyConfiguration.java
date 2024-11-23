package org.mpisws.strategies;

public class SchedulingStrategyConfiguration {
    private Long seed;

    private SchedulingStrategyConfiguration() {}

    public static class Builder {
        private Long seed;

        public Builder() {
            this.seed = null;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public SchedulingStrategyConfiguration build() {
            SchedulingStrategyConfiguration config = new SchedulingStrategyConfiguration();
            config.seed = this.seed;
            return config;
        }
    }
}
