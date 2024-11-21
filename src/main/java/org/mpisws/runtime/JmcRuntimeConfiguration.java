package org.mpisws.runtime;

import org.mpisws.checker.GraphExploration;
import org.mpisws.strategies.SchedulingStrategy;

public class JmcRuntimeConfiguration {
    private String strategy;
    private SchedulingStrategy customStrategy;

    private Integer iterations;

    private Boolean debug;

    private String bugsPath;

    // To be deprecated
    private GraphExploration graphExploration;

    private JmcRuntimeConfiguration() {}

    public String getStrategy() {
        return strategy;
    }

    public SchedulingStrategy getCustomStrategy() {
        return customStrategy;
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

    public boolean isCustomStrategy() {
        return customStrategy != null;
    }

    public GraphExploration getGraphExploration() {
        return graphExploration;
    }

    public static class Builder {
        private String strategy;
        private SchedulingStrategy customStrategy;
        private Integer iterations;
        private Boolean debug;
        private String bugsPath;
        private GraphExploration graphExploration;

        public Builder() {
            this.strategy = "random";
            this.customStrategy = null;
            this.iterations = 1;
            this.debug = false;
            this.bugsPath = "build/test-results/jmc-bugs";
            this.graphExploration = GraphExploration.BFS;
        }

        public Builder strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder customStrategy(SchedulingStrategy customStrategy) {
            this.customStrategy = customStrategy;
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

        public Builder graphExploration(GraphExploration graphExploration) {
            this.graphExploration = graphExploration;
            return this;
        }

        public JmcRuntimeConfiguration build() {
            JmcRuntimeConfiguration config = new JmcRuntimeConfiguration();
            config.strategy = strategy;
            config.customStrategy = customStrategy;
            config.iterations = iterations;
            config.debug = debug;
            config.bugsPath = bugsPath;
            return config;
        }
    }
}
