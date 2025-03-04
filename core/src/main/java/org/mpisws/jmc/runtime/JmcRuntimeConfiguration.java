package org.mpisws.jmc.runtime;

import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.SchedulingStrategy;

public class JmcRuntimeConfiguration {

    private SchedulingStrategy strategy;

    private Boolean debug;

    private String reportPath;

    private JmcRuntimeConfiguration() {}

    public SchedulingStrategy getStrategy() {
        return strategy;
    }

    public Boolean getDebug() {
        return debug;
    }

    public String getReportPath() {
        return reportPath;
    }

    public static class Builder {
        private SchedulingStrategy strategy;
        private Boolean debug;
        private String reportPath;

        public Builder() {
            this.strategy = new RandomSchedulingStrategy(System.nanoTime());
            this.debug = false;
            this.reportPath = "build/test-results/jmc-report";
        }

        public Builder strategy(SchedulingStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder debug(Boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder reportPath(String reportPath) {
            this.reportPath = reportPath;
            return this;
        }

        public JmcRuntimeConfiguration build() {
            JmcRuntimeConfiguration config = new JmcRuntimeConfiguration();
            config.strategy = strategy;
            config.debug = debug;
            config.reportPath = reportPath;
            return config;
        }
    }
}
