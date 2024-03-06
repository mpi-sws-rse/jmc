package org.example.checker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

import java.io.Serializable;
import java.util.Random;

import org.example.checker.CheckerConfiguration.StrategyOption.RandomStrategy;

public final class CheckerConfiguration implements Serializable {
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

    public byte[] generateBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(this);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            assert(false);
        }
        return bos.toByteArray();
    }

    public void saveConfig(String fileName) {
        try {
            FileOutputStream fileOut = new FileOutputStream(fileName);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
            out.close();
            fileOut.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static class ConfigurationBuilder {
        public long maxEventsPerExecution = 1000;
        public long maxIterations = 1000;
        public long progressReport = 0;
        public org.example.checker.CheckerConfiguration.StrategyOption searchStrategy = new RandomStrategy();
        public boolean verbose = false;
        public long seed = new Random().nextLong(); // can be overwritten to a user-specified seed for reproducibility

        public ConfigurationBuilder() {
        }

        public CheckerConfiguration build() {
            return new CheckerConfiguration(this);
        }

        public ConfigurationBuilder withMaxEventsPerExexution(long m) {
            this.maxEventsPerExecution = m;
            return this;
        }

        public ConfigurationBuilder withSearchStrategy(StrategyOption option) {
            this.searchStrategy = option;
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

    public static sealed interface StrategyOption {
        record RandomStrategy() implements StrategyOption {
        }

        record DPORStrategy() implements StrategyOption {
        }
    }

}
