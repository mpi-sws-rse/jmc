package org.example.checker;

public final class CheckerConfiguration {
    public long maxEventsPerExecution;
    public long maxIterations;
    public long progressReport;
    public boolean verbose;
    public long seed;

    private CheckerConfiguration(ConfigurationBuilder builder) {
        maxEventsPerExecution = builder.maxEventsPerExecution;
        maxIterations = builder.maxIterations;
        progressReport = builder.progressReport;
        verbose = builder.verbose;
        seed = builder.seed;
    }

    public static class ConfigurationBuilder {
        public long maxEventsPerExecution = 1000;
        public long maxIterations = 1000;
        public long progressReport = 0;
        public boolean verbose = false;
        public long seed = 0;

        ConfigurationBuilder() {

        }

        public CheckerConfiguration build() {
            return new CheckerConfiguration(this);
        }

        ConfigurationBuilder withMaxEventsPerExexution(long m) {
            this.maxEventsPerExecution = m;
            return this;
        }

        ConfigurationBuilder withMaxIterations(long m) {
            this.maxIterations = m;
            return this;
        }

        ConfigurationBuilder withProgressRepose(long m) {
            this.progressReport = m;
            return this;
        }

        ConfigurationBuilder withSeed(long m) {
            this.seed = m;
            return this;
        }

        ConfigurationBuilder withVerbose(boolean t) {
            this.verbose = t;
            return this;
        }

    }

}
