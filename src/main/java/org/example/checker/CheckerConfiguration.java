package org.example.checker;

import java.io.*;
import java.util.Random;

public final class CheckerConfiguration implements Serializable {
    public long maxEventsPerExecution;
    public long progressReport;
    public boolean verbose;
    public int maxIterations;
    public long seed;
    public StrategyType strategyType;

    private CheckerConfiguration(ConfigurationBuilder builder) {
        maxEventsPerExecution = builder.maxEventsPerExecution;
        maxIterations = builder.maxIterations;
        progressReport = builder.progressReport;
        verbose = builder.verbose;
        seed = builder.seed;
        strategyType = builder.strategyType;
    }

    public byte[] generateBytes() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate bytes", e);
        }
    }

    public void saveConfig(String fileName) {
        try (FileOutputStream fileOut = new FileOutputStream(fileName);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(this);
        } catch (IOException i) {
            throw new RuntimeException("Failed to save configuration", i);
        }
    }

    public static class ConfigurationBuilder {
        public long maxEventsPerExecution = 100;
        public int maxIterations = 100;
        public long progressReport = 0;
        public boolean verbose = false;
        public long seed = new Random().nextLong();
        public StrategyType strategyType = StrategyType.RANDOMSTRAREGY;

        public ConfigurationBuilder() {
        }

        public CheckerConfiguration build() {
            return new CheckerConfiguration(this);
        }

        public ConfigurationBuilder withMaxEventsPerExecution(long maxEventsPerExecution) {
            this.maxEventsPerExecution = maxEventsPerExecution;
            return this;
        }

        public ConfigurationBuilder withMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public ConfigurationBuilder withProgressReport(long progressReport) {
            this.progressReport = progressReport;
            return this;
        }

        public ConfigurationBuilder withSeed(long seed) {
            this.seed = seed;
            return this;
        }

        public ConfigurationBuilder withVerbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public ConfigurationBuilder withStrategyType(StrategyType strategyType) {
            this.strategyType = strategyType;
            return this;
        }

    }
}
