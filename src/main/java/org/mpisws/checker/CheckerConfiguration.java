package org.mpisws.checker;

import org.mpisws.solver.SMTSolverTypes;
import org.mpisws.solver.SolverApproach;

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
     * @property {@link #programType} determines whether the program uses shared memory or message passing.
     */
    public ProgramType programType;

    public GraphExploration graphExploration;

    public SolverApproach solverApproach;

    public SchedulingPolicy schedulingPolicy;

    public int[][] inputIntegers;

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
        programType = builder.programType;
        graphExploration = builder.graphExploration;
        solverApproach = builder.solverApproach;
        schedulingPolicy = builder.schedulingPolicy;
        inputIntegers = builder.inputIntegers;
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

    // This method gets two inputs. First is an array of integers and second is an integer value. The first
    // input ia 1D array which each element is member of data domain. Thus  if the array is a[0] = 0, a[1] = 1, a[2] = 2
    // then the data domain is {0, 1, 2}. The second input indicates the of each possible sequence of the data domain.
    // Thus if the size is 3 then there are 3^3 = 27 possible sequences. The method should fill the 2D array inputIntegers
    // with all possible sequences of the data domain. The method should return the 2D array inputIntegers.
    @Deprecated
    public void fillInputIntegers(int[] dataDomain, int size) {
        int[][] inputIntegers = new int[(int) Math.pow(dataDomain.length, size)][size];
        int index = 0;
        for (int i = 0; i < dataDomain.length; i++) {
            for (int j = 0; j < dataDomain.length; j++) {
                for (int k = 0; k < dataDomain.length; k++) {
                    inputIntegers[index][0] = dataDomain[i];
                    inputIntegers[index][1] = dataDomain[j];
                    inputIntegers[index][2] = dataDomain[k];
                    index++;
                }
            }
        }
        this.inputIntegers = inputIntegers;
    }

    public void generateSequences(int[] array, int length) {
        int numOfSequences = (int) Math.pow(array.length, length);
        int[][] inputIntegers = new int[numOfSequences][length];

        generate(inputIntegers, array, length, 0, new int[length], 0);

        this.inputIntegers = inputIntegers;
    }

    private void generate(int[][] inputIntegers, int[] array, int length, int index, int[] current, int position) {
        if (index == length) {
            // Store the current sequence in the 2D array
            inputIntegers[position] = current.clone(); // Use clone to avoid reference issues
            return;
        }

        for (int i = 0; i < array.length; i++) {
            current[index] = array[i];
            generate(inputIntegers, array, length, index + 1, current, position * array.length + i);
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
        public ProgramType programType = ProgramType.SHARED_MEM;
        public GraphExploration graphExploration = GraphExploration.DFS;
        public SolverApproach solverApproach = SolverApproach.NO_SOLVER;
        public SchedulingPolicy schedulingPolicy = SchedulingPolicy.FIFO;
        public int[][] inputIntegers = null;

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

        public ConfigurationBuilder withProgramType(ProgramType programType) {
            this.programType = programType;
            return this;
        }

        public ConfigurationBuilder withGraphExploration(GraphExploration graphExploration) {
            this.graphExploration = graphExploration;
            return this;
        }

        public ConfigurationBuilder withSolverApproach(SolverApproach solverApproach) {
            this.solverApproach = solverApproach;
            return this;
        }

        public ConfigurationBuilder withSchedulingPolicy(SchedulingPolicy schedulingPolicy) {
            this.schedulingPolicy = schedulingPolicy;
            return this;
        }

        public ConfigurationBuilder withInputIntegers(int[][] inputIntegers) {
            this.inputIntegers = inputIntegers;
            return this;
        }
    }
}