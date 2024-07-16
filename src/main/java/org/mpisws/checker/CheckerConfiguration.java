package org.mpisws.checker;

import org.mpisws.solver.SMTSolverTypes;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Random;

/**
 * The CheckerConfiguration class is responsible for managing the configuration of the checker. It maintains several
 * configuration parameters including the maximum number of events per execution, progress report interval, verbosity,
 * maximum number of iterations, seed for random number generator, and the strategy type.
 * The class provides functionality to generate a byte array of the configuration object and to save the configuration
 * to a file. The class uses the Serializable interface to serialize and deserialize the configuration object.
 * The class requires a ConfigurationBuilder object upon construction, which is used to set the configuration parameters.
 * The ConfigurationBuilder class is a static inner class of the CheckerConfiguration class and is used to build the
 * configuration object. The CheckerConfiguration class is designed to manage the configuration of the checker and to
 * provide an easy way to set and save the configuration parameters.
 */
public final class CheckerConfiguration implements Serializable {

    /**
     * @property {@link #maxEventsPerExecution} maximum number of events to be executed in a single execution
     */
    public long maxEventsPerExecution;

    /**
     * @property {@link #progressReport} progress report interval
     */
    public long progressReport;

    /**
     * @property {@link #verbose} verbose mode
     */
    public boolean verbose;

    /**
     * @property {@link #maxIterations} maximum number of iterations
     */
    public int maxIterations;

    /**
     * @property {@link #seed} seed for random number generator
     */
    public long seed;

    /**
     * @property {@link #strategyType} strategy type to be used
     */
    public StrategyType strategyType;

    /**
     * @property {@link #buggyTracePath} path to the buggy trace object
     */
    public String buggyTracePath;

    /**
     * @property {@link #buggyTraceFile} name of the buggy trace file
     */
    public String buggyTraceFile;

    /**
     * @property {@link #executionGraphsPath} path to the visualized execution graphs
     */
    public String executionGraphsPath;

    /**
     * @property {@link #solverType} solver types to be used
     */
    public SMTSolverTypes solverType;

    /**
     * The following constructor is used to initialize the configuration with default values.
     * <br>
     * This constructor is private and only accessible through the builder.
     *
     * @param builder the builder to be used to initialize the configuration
     */
    private CheckerConfiguration(ConfigurationBuilder builder) {
        maxEventsPerExecution = builder.maxEventsPerExecution;
        maxIterations = builder.maxIterations;
        progressReport = builder.progressReport;
        verbose = builder.verbose;
        seed = builder.seed;
        strategyType = builder.strategyType;
        buggyTracePath = builder.buggyTracePath;
        executionGraphsPath = builder.executionGraphsPath;
        buggyTraceFile = builder.buggyTraceFile;
        solverType = builder.solverType;
    }

    /**
     * Generates the byte array of the configuration object.
     *
     * @return the bytes of the configuration
     * @throws RuntimeException if the bytes cannot be generated
     */
    public byte[] generateBytes() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate bytes", e);
        }
    }

    /**
     * Saves the configuration to a given file name.
     *
     * @param fileName the file name to load the configuration from
     * @throws RuntimeException if the configuration cannot be saved
     */
    public void saveConfig(String fileName) {
        try (FileOutputStream fileOut = new FileOutputStream(fileName);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(this);
        } catch (IOException i) {
            throw new RuntimeException("Failed to save configuration", i);
        }
    }

    /**
     * The following static class is used to build the configuration object.
     * <br>
     * The builder is used to set the configuration parameters and then build the configuration object.
     * It provides default values for the configuration parameters. Additionally, it provides methods to set the
     * configuration parameters.
     */
    public static class ConfigurationBuilder {

        public long maxEventsPerExecution = 100;
        public int maxIterations = 10;
        public long progressReport = 0;
        public boolean verbose = false;
        public long seed = new Random().nextLong();
        public StrategyType strategyType = StrategyType.REPLAY;
        public String buggyTracePath = "src/main/resources/buggyTrace/";
        public String buggyTraceFile = "buggyTrace.obj";
        public String executionGraphsPath = "src/main/resources/Visualized_Graphs/";
        public SMTSolverTypes solverType = SMTSolverTypes.SMTINTERPOL;

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

        public ConfigurationBuilder withBuggyTracePath(String buggyTracePath) {
            this.buggyTracePath = buggyTracePath;
            return this;
        }

        public ConfigurationBuilder withExecutionGraphsPath(String executionGraphsPath) {
            this.executionGraphsPath = executionGraphsPath;
            return this;
        }

        public ConfigurationBuilder withBuggyTraceFile(String buggyTraceFile) {
            this.buggyTraceFile = buggyTraceFile;
            return this;
        }

        public ConfigurationBuilder withSolverType(SMTSolverTypes solverType) {
            this.solverType = solverType;
            return this;
        }
    }
}