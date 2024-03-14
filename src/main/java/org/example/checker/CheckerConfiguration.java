package org.example.checker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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

        public ConfigurationBuilder withMaxEventsPerExexution(long m) {
            this.maxEventsPerExecution = m;
            return this;
        }


        ConfigurationBuilder withMaxIterations(int m) {
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

        ConfigurationBuilder withStrategyType(StrategyType t) {
            this.strategyType = t;
            return this;
        }

    }
}
