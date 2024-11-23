package org.mpisws.runtime;

import org.mpisws.strategies.RandomSchedulingStrategy;
import org.mpisws.strategies.SchedulingStrategy;

public class JmcRuntimeConfiguration {

    private SchedulingStrategy strategy;

    private Integer iterations;

    private Boolean debug;

    private String bugsPath;

    private JmcRuntimeConfiguration() {}

    public SchedulingStrategy getStrategy() {
        return strategy;
    }

    public Integer getIterations() {
        return iterations;
    }

    public Boolean getDebug() {
        return debug;
    }

    public String getBugsPath() {
        return bugsPath;
    }

    public static class Builder {
        private SchedulingStrategy strategy;
        private Integer iterations;
        private Boolean debug;
        private String bugsPath;

        public Builder() {
            this.strategy = new RandomSchedulingStrategy(System.nanoTime());
            this.iterations = 1;
            this.debug = false;
            this.bugsPath = "build/test-results/jmc-bugs";
        }

        public Builder strategy(SchedulingStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder iterations(Integer iterations) {
            this.iterations = iterations;
            return this;
        }

        public Builder debug(Boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder bugsPath(String bugsPath) {
            this.bugsPath = bugsPath;
            return this;
        }

        public JmcRuntimeConfiguration build() {
            JmcRuntimeConfiguration config = new JmcRuntimeConfiguration();
            config.strategy = strategy;
            config.iterations = iterations;
            config.debug = debug;
            config.bugsPath = bugsPath;
            return config;
        }
    }
}
