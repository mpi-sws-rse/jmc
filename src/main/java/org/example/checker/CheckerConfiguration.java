package org.example.checker;
import java.util.Random;

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
        System.out.println("Random seed: " + seed);
    }

    public static class ConfigurationBuilder {
        public long maxEventsPerExecution = 1000;
        public long maxIterations = 1000;
        public long progressReport = 0;
        public boolean verbose = false;
        public long seed = new Random().nextLong(); // can be overwritten to a user-specified seed for reproducibility

        public ConfigurationBuilder() {

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
