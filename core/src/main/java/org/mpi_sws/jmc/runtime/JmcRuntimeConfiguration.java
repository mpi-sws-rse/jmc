package org.mpi_sws.jmc.runtime;

import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;
import org.mpi_sws.jmc.strategies.SchedulingStrategy;

/**
 * Represents the configuration for the JMC runtime.
 *
 * <p>This class encapsulates various settings that control the behavior of the JMC runtime,
 * including scheduling strategies, debugging options, report paths, and retry configurations.
 *
 * <p>Use the {@link JmcRuntimeConfiguration.Builder} to create a configuration instance.
 *
 * <p>The user does not have to specify this explicitly. The {@link
 * JmcCheckerConfiguration} provided is used to create and instance of this
 * class
 */
public class JmcRuntimeConfiguration {

    /** The scheduling strategy that drives the run. */
    private SchedulingStrategy strategy;

    /** Whether debug logging (including per-iteration log files) is enabled. */
    private Boolean debug;

    /** Directory where logs and trace artifacts are written. */
    private String reportPath;

    /**
     * Number of times the scheduler thread retries {@code strategy.nextTask()} before giving up when
     * no task is runnable yet.
     */
    private int schedulerTries = 10;

    /** Sleep, in nanoseconds, between scheduler retries. */
    private long schedulerTrySleepTimeNanos = 100;

    /** Private constructor; instances are created through {@link Builder}. */
    private JmcRuntimeConfiguration() {}

    /**
     * Returns the configured scheduling strategy.
     *
     * @return the scheduling strategy
     */
    public SchedulingStrategy getStrategy() {
        return strategy;
    }

    /**
     * Returns whether debug logging is enabled.
     *
     * @return {@code true} if debug logging is enabled
     */
    public Boolean getDebug() {
        return debug;
    }

    /**
     * Returns the report output directory.
     *
     * @return the report path
     */
    public String getReportPath() {
        return reportPath;
    }

    /**
     * Returns the scheduler retry count.
     *
     * @return the number of scheduler retries
     */
    public int getSchedulerTries() {
        return schedulerTries;
    }

    /**
     * Returns the sleep between scheduler retries.
     *
     * @return the scheduler retry sleep time in nanoseconds
     */
    public long getSchedulerTrySleepTimeNanos() {
        return schedulerTrySleepTimeNanos;
    }

    /**
     * Builder for {@link JmcRuntimeConfiguration}.
     *
     * <p>All values are seeded with defaults (a random scheduling strategy, debug off, the default
     * report path, 10 scheduler tries, and a 100ns retry sleep) and can be overridden fluently.
     */
    public static class Builder {
        /** The scheduling strategy to build with. */
        private SchedulingStrategy strategy;
        /** The debug flag to build with. */
        private Boolean debug;
        /** The report path to build with. */
        private String reportPath;
        /** The scheduler retry count to build with. */
        private int schedulerTries;
        /** The scheduler retry sleep (ns) to build with. */
        private long schedulerTrySleepTimeNanos;

        /** Creates a builder pre-populated with the default configuration values. */
        public Builder() {
            this.strategy =
                    new RandomSchedulingStrategy(
                            System.nanoTime(), "build/test-results/jmc-report");
            this.debug = false;
            this.reportPath = "build/test-results/jmc-report";
            this.schedulerTries = 10;
            this.schedulerTrySleepTimeNanos = 100;
        }

        /**
         * Sets the scheduling strategy.
         *
         * @param strategy the scheduling strategy
         * @return this builder, for chaining
         */
        public Builder strategy(SchedulingStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /**
         * Sets the debug flag.
         *
         * @param debug whether debug logging is enabled
         * @return this builder, for chaining
         */
        public Builder debug(Boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * Sets the report output directory.
         *
         * @param reportPath the report path
         * @return this builder, for chaining
         */
        public Builder reportPath(String reportPath) {
            this.reportPath = reportPath;
            return this;
        }

        /**
         * Sets the scheduler retry count.
         *
         * @param schedulerTries the number of scheduler retries
         * @return this builder, for chaining
         */
        public Builder schedulerTries(int schedulerTries) {
            this.schedulerTries = schedulerTries;
            return this;
        }

        /**
         * Sets the sleep between scheduler retries.
         *
         * @param schedulerTrySleepTimeNanos the retry sleep time in nanoseconds
         * @return this builder, for chaining
         */
        public Builder schedulerTrySleepTimeNanos(long schedulerTrySleepTimeNanos) {
            this.schedulerTrySleepTimeNanos = schedulerTrySleepTimeNanos;
            return this;
        }

        /**
         * Builds an immutable {@link JmcRuntimeConfiguration} from the configured values.
         *
         * @return a new {@link JmcRuntimeConfiguration} instance
         */
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
