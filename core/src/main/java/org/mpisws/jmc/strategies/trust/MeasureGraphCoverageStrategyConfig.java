package org.mpisws.jmc.strategies.trust;

import java.time.Duration;

public class MeasureGraphCoverageStrategyConfig {
    private boolean debug;
    private boolean recordGraphs;
    private String recordPath;
    private Duration measuringFrequency;
    private boolean recordPerIteration;

    private MeasureGraphCoverageStrategyConfig() {}

    public boolean isDebugEnabled() {
        return debug;
    }

    public boolean shouldRecordGraphs() {
        return recordGraphs;
    }

    public String getRecordPath() {
        return recordPath;
    }

    public Duration getMeasuringFrequency() {
        return measuringFrequency;
    }

    public boolean isRecordPerIteration() {
        return recordPerIteration;
    }

    public static MeasureGraphCoverageStrategyConfigBuilder builder() {
        return new MeasureGraphCoverageStrategyConfigBuilder();
    }

    public static class MeasureGraphCoverageStrategyConfigBuilder {

        private boolean debug;
        private boolean recordGraphs;
        private String recordPath;
        private Duration measuringFrequency;
        private boolean recordPerIteration;

        public MeasureGraphCoverageStrategyConfigBuilder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public MeasureGraphCoverageStrategyConfigBuilder recordGraphs(boolean recordGraphs) {
            this.recordGraphs = recordGraphs;
            return this;
        }

        public MeasureGraphCoverageStrategyConfigBuilder recordPath(String recordPath) {
            this.recordPath = recordPath;
            return this;
        }

        public MeasureGraphCoverageStrategyConfigBuilder withFrequency(
                Duration measuringFrequency) {
            this.measuringFrequency = measuringFrequency;
            return this;
        }

        public MeasureGraphCoverageStrategyConfigBuilder recordPerIteration() {
            this.recordPerIteration = true;
            return this;
        }

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
