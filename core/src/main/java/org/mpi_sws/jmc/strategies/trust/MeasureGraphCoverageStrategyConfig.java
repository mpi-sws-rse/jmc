package org.mpi_sws.jmc.strategies.trust;

import java.time.Duration;

/**
 * Configuration class for the MeasureGraphCoverageStrategy.
 *
 * <p>This class provides a builder pattern to create instances of the configuration with various
 * options such as enabling debug mode, recording graphs, setting the record path, measuring
 * frequency, and whether to record per iteration.
 */
public class MeasureGraphCoverageStrategyConfig {
    /** Whether to dump individual graphs to disk as they are discovered. */
    private boolean debug;
    /** Whether to write the per-graph hash counts at teardown. */
    private boolean recordGraphs;
    /** Directory where coverage artifacts are written. */
    private String recordPath;
    /** Sampling period for time-based coverage measurement (mutually exclusive with per-iteration). */
    private Duration measuringFrequency;
    /** Whether coverage is sampled once per iteration instead of on a timer. */
    private boolean recordPerIteration;

    /** Use {@link #builder()} to construct instances. */
    private MeasureGraphCoverageStrategyConfig() {}

    /**
     * @return whether per-graph debug dumping is enabled.
     */
    public boolean isDebugEnabled() {
        return debug;
    }

    /**
     * @return whether per-graph hash counts are written at teardown.
     */
    public boolean shouldRecordGraphs() {
        return recordGraphs;
    }

    /**
     * @return the directory for coverage artifacts.
     */
    public String getRecordPath() {
        return recordPath;
    }

    /**
     * @return the sampling period, or {@code null} in per-iteration mode.
     */
    public Duration getMeasuringFrequency() {
        return measuringFrequency;
    }

    /**
     * @return whether coverage is sampled once per iteration.
     */
    public boolean isRecordPerIteration() {
        return recordPerIteration;
    }

    /**
     * Returns a new builder.
     *
     * @return a configuration builder.
     */
    public static MeasureGraphCoverageStrategyConfigBuilder builder() {
        return new MeasureGraphCoverageStrategyConfigBuilder();
    }

    /** Builder for {@link MeasureGraphCoverageStrategyConfig}. */
    public static class MeasureGraphCoverageStrategyConfigBuilder {

        private boolean debug;
        private boolean recordGraphs;
        private String recordPath;
        private Duration measuringFrequency;
        private boolean recordPerIteration;

        /**
         * Enables or disables per-graph debug dumping.
         *
         * @param debug the flag value.
         * @return this builder.
         */
        public MeasureGraphCoverageStrategyConfigBuilder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * Enables or disables writing per-graph hash counts at teardown.
         *
         * @param recordGraphs the flag value.
         * @return this builder.
         */
        public MeasureGraphCoverageStrategyConfigBuilder recordGraphs(boolean recordGraphs) {
            this.recordGraphs = recordGraphs;
            return this;
        }

        /**
         * Sets the directory for coverage artifacts.
         *
         * @param recordPath the output directory.
         * @return this builder.
         */
        public MeasureGraphCoverageStrategyConfigBuilder recordPath(String recordPath) {
            this.recordPath = recordPath;
            return this;
        }

        /**
         * Selects time-based sampling at the given period.
         *
         * @param measuringFrequency the sampling period.
         * @return this builder.
         */
        public MeasureGraphCoverageStrategyConfigBuilder withFrequency(
                Duration measuringFrequency) {
            this.measuringFrequency = measuringFrequency;
            return this;
        }

        /**
         * Selects per-iteration sampling (instead of a timer).
         *
         * @return this builder.
         */
        public MeasureGraphCoverageStrategyConfigBuilder recordPerIteration() {
            this.recordPerIteration = true;
            return this;
        }

        /**
         * Validates and builds the configuration.
         *
         * @return the built configuration.
         * @throws IllegalArgumentException if the record path is missing, or the sampling mode is
         *     unset or ambiguous.
         */
        public MeasureGraphCoverageStrategyConfig build() {
            if (this.recordPath == null || this.recordPath.isEmpty()) {
                throw new IllegalArgumentException("Record path cannot be null or empty");
            }
            if (this.measuringFrequency == null && !this.recordPerIteration) {
                throw new IllegalArgumentException(
                        "Measuring frequency or record per iteration must be set");
            }
            if (this.measuringFrequency != null && this.recordPerIteration) {
                throw new IllegalArgumentException(
                        "Measuring frequency and record per iteration cannot be used together");
            }
            MeasureGraphCoverageStrategyConfig config = new MeasureGraphCoverageStrategyConfig();
            config.debug = this.debug;
            config.recordGraphs = this.recordGraphs;
            config.recordPath = this.recordPath;
            config.measuringFrequency = this.measuringFrequency;
            config.recordPerIteration = this.recordPerIteration;
            return config;
        }
    }
}
