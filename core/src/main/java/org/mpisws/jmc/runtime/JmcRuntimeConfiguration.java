package org.mpisws.jmc.runtime;

import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.SchedulingStrategy;

public class JmcRuntimeConfiguration {

    private SchedulingStrategy strategy;

    private Boolean debug;

    private String reportPath;

    private int schedulerTries = 10;

    private long schedulerTrySleepTimeNanos = 100;

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

    public int getSchedulerTries() {
        return schedulerTries;
    }

    public long getSchedulerTrySleepTimeNanos() {
        return schedulerTrySleepTimeNanos;
    }

    public static class Builder {
        private SchedulingStrategy strategy;
        private Boolean debug;
        private String reportPath;
        private int schedulerTries;
        private long schedulerTrySleepTimeNanos;

        public Builder() {
            this.strategy = new RandomSchedulingStrategy(System.nanoTime());
            this.debug = false;
            this.reportPath = "build/test-results/jmc-report";
            this.schedulerTries = 10;
            this.schedulerTrySleepTimeNanos = 100;
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

        public Builder schedulerTries(int schedulerTries) {
            this.schedulerTries = schedulerTries;
            return this;
        }

        public Builder schedulerTrySleepTimeNanos(long schedulerTrySleepTimeNanos) {
            this.schedulerTrySleepTimeNanos = schedulerTrySleepTimeNanos;
            return this;
        }

        public JmcRuntimeConfiguration build() {
            JmcRuntimeConfiguration config = new JmcRuntimeConfiguration();
            config.strategy = strategy;
            config.debug = debug;
            config.reportPath = reportPath;
            config.schedulerTries = schedulerTries;
            config.schedulerTrySleepTimeNanos = schedulerTrySleepTimeNanos;
            return config;
        }
    }
}
