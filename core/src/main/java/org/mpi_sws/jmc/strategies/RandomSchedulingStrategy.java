package org.mpi_sws.jmc.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.tracker.TrackActiveTasksStrategy;
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A random scheduling strategy that selects the next thread to be scheduled randomly.
 */
public class RandomSchedulingStrategy extends TrackActiveTasksStrategy
        implements ReplayableSchedulingStrategy {

    private static final Logger LOGGER = LogManager.getLogger(RandomSchedulingStrategy.class);

    protected final ExtRandom random;
    protected final HashMap<Long, Integer> randomValueMap;

    private final String reportPath;
    protected RandomSchedulingTrace curTrace;

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public RandomSchedulingStrategy(Long seed, String reportPath) {
        this.random = new ExtRandom(seed);
        this.randomValueMap = new HashMap<>();
        this.reportPath = reportPath;
        this.curTrace = new RandomSchedulingTrace(seed);
    }

    /**
     * This method initializes the strategy for a new iteration. It sets the replay seed in the report and
     * clears the random value map for the new iteration. It also initializes the current trace with the seed.
     * @param iteration the number of the iteration.
     * @param report
     * @throws HaltExecutionException
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report)
            throws HaltExecutionException {
        super.initIteration(iteration, report);
        report.setReplaySeed(random.getSeed());
        LOGGER.debug("Seed for iteration {} is {}", iteration, random.getSeed());
        randomValueMap.clear();

        curTrace = new RandomSchedulingTrace(random.getSeed());
    }

    /**
     * Returns the next task to be scheduled. The task is picked randomly from the set of active
     * tasks.
     *
     * @return the next task to be scheduled
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        Long taskToSchedule;
        Set<Long> activeThreads = getActiveTasks();
        if (activeThreads.isEmpty()) {
            return null;
        }
        if (activeThreads.size() == 1) {
            taskToSchedule = (Long) activeThreads.toArray()[0];
        } else {
            int index = random.nextInt(activeThreads.size());
            taskToSchedule = (Long) activeThreads.toArray()[index];
        }
        return makeSchedulingChoice(taskToSchedule);
    }

    protected SchedulingChoice<?> makeSchedulingChoice(Long taskToSchedule) {
        SchedulingChoice<?> choice = SchedulingChoice.task(taskToSchedule);
        if (randomValueMap.containsKey(taskToSchedule)) {
            int randomValue = randomValueMap.remove(taskToSchedule);
            LOGGER.debug("Using cached random value {} for task {}", randomValue, taskToSchedule);
            choice = SchedulingChoice.task(taskToSchedule, new PrimitiveValue(randomValue));
        }
        curTrace.addChoice(choice);
        return choice;
    }

    // Keep track of reactive events that need a return value
    @Override
    public void updateEvent(JmcRuntimeEvent event)
            throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        if (event.getType() == JmcRuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE) {
            Long taskId = event.getTaskId();
            Integer bits = event.getParam("bits");
            int randomValue = random.next(bits);
            randomValueMap.put(taskId, randomValue);
            LOGGER.debug("Generated random value {} for task {}", randomValue, taskId);
        }
    }

    @Override
    public void recordTrace() throws JmcCheckerException {
        String seedFilePath = Paths.get(this.reportPath, "replay_seed.txt").toString();
        String traceFilePath = Paths.get(this.reportPath, "replay_trace.json").toString();
        FileUtil.unsafeStoreToFile(seedFilePath, this.curTrace.getSeed() + "\n");
        FileUtil.storeTaskSchedule(traceFilePath, this.curTrace.getChoices());
    }

    @Override
    public void replayRecordedTrace() throws JmcCheckerException {
        // TODO: complete this
    }

    /**
     * ExtRandom mirrors {@link Random} while exposing its internal seed for replay and
     * validating that the local Linear Congruential Generator (LCG) step matches {@link Random#next(int)}.
     */
    protected static class ExtRandom extends Random {
        /**
         * Current LCG state (scrambled seed), kept as an {@link AtomicLong} for atomic updates.
         */
        private AtomicLong seed;

        /**
         * LCG multiplier used by {@link Random}.
         */
        private static final long multiplier = 0x5DEECE66DL;
        /**
         * LCG addend used by {@link Random}.
         */
        private static final long addend = 0xBL;
        /**
         * Bit mask for the 48-bit LCG state used by {@link Random}.
         */
        private static final long mask = (1L << 48) - 1;

        /**
         * Creates a new random generator initialized with the provided seed.
         * The {@link Random} constructor invokes {@link #setSeed(long)}.
         */
        public ExtRandom(long seed) {
            super(seed);
        }

        /**
         * Scrambles the external seed to the 48-bit internal LCG state.
         */
        private static long initialScramble(long seed) {
            return (seed ^ multiplier) & mask;
        }

        /**
         * Resets the generator with a new seed and updates the exposed atomic state.
         */
        @Override
        public synchronized void setSeed(long seed) {
            super.setSeed(seed);
            this.seed = new AtomicLong(initialScramble(seed));
        }

        /**
         * Returns the current scrambled LCG seed (used for replay reporting and
         * reinitializing the current trace's seed)
         */
        public Long getSeed() {
            return seed.get();
        }

        /**
         * Advances the LCG state, verifies it matches {@link Random#next(int)},
         * and returns the generated value for the requested bit width.
         */
        @Override
        public int next(int bits) {
            int orig = super.next(bits);
            long oldseed, nextseed;
            AtomicLong seed = this.seed;
            do {
                oldseed = seed.get();
                nextseed = (oldseed * multiplier + addend) & mask;
            } while (!seed.compareAndSet(oldseed, nextseed));
            int computed = (int) (nextseed >>> (48 - bits));
            if (computed != orig) {
                LOGGER.error("Random number generation mismatch: {} != {}", computed, orig);
                throw new RuntimeException("Random number generation mismatch");
            }
            return orig;
        }
    }

    protected static class RandomSchedulingTrace {
        private final long seed;
        private final List<SchedulingChoice<?>> choices;

        public RandomSchedulingTrace(long seed) {
            this.seed = seed;
            this.choices = new ArrayList<>();
        }

        public void addChoice(SchedulingChoice<?> choice) {
            choices.add(choice);
        }

        public long getSeed() {
            return seed;
        }

        public List<SchedulingChoice<?>> getChoices() {
            return choices;
        }
    }
}
