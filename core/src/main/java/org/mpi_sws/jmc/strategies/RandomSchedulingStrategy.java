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
 * A scheduling strategy that, at each step, picks the next task uniformly at random from the set of
 * currently runnable tasks.
 *
 * <p>It extends {@link TrackActiveTasksStrategy} to obtain the set of runnable tasks (computed by the
 * trackers) and implements {@link ReplayableSchedulingStrategy} so that a run can be reproduced from
 * its seed. It also answers reactive random-value events ({@link
 * JmcRuntimeEvent.Type#REACTIVE_EVENT_RANDOM_VALUE}) and records every choice into a {@link
 * RandomSchedulingTrace} for replay.
 */
public class RandomSchedulingStrategy extends TrackActiveTasksStrategy
        implements ReplayableSchedulingStrategy {

    /** Logger used to trace seeds, generated random values, and cached choices. */
    private static final Logger LOGGER = LogManager.getLogger(RandomSchedulingStrategy.class);

    /** Seeded random number generator that also exposes its seed for replay. */
    protected final ExtRandom random;

    /**
     * Pending reactive random values, keyed by the task that requested them. A value is produced in
     * {@link #updateEvent(JmcRuntimeEvent)} and consumed (attached to the choice) in {@link
     * #makeSchedulingChoice(Long)}.
     */
    protected final HashMap<Long, Integer> randomValueMap;

    /** Directory where the replay seed and trace are written by {@link #recordTrace()}. */
    private final String reportPath;

    /** The schedule (seed plus ordered choices) recorded for the current iteration. */
    protected RandomSchedulingTrace curTrace;

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     * @param reportPath the directory where the replay seed and trace are written
     */
    public RandomSchedulingStrategy(Long seed, String reportPath) {
        this.random = new ExtRandom(seed);
        this.randomValueMap = new HashMap<>();
        this.reportPath = reportPath;
        this.curTrace = new RandomSchedulingTrace(seed);
    }

    /**
     * Initializes the strategy for a new iteration.
     *
     * <p>Delegates to the superclass (resetting tracker-derived state), records the current RNG seed
     * into the report for replay, clears the pending reactive random values, and starts a fresh
     * trace seeded with the current seed.
     *
     * @param iteration the number of the iteration
     * @param report the model checker report; the replay seed is recorded into it
     * @throws HaltExecutionException if initialization fails and the execution must halt
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

    /**
     * Builds the scheduling choice for the chosen task and records it in the current trace.
     *
     * <p>If a reactive random value is pending for the task (see {@link
     * #updateEvent(JmcRuntimeEvent)}), it is consumed from {@link #randomValueMap} and attached to
     * the choice as a {@link PrimitiveValue} so it is delivered to the task on resume.
     *
     * @param taskToSchedule the ID of the task to schedule
     * @return the scheduling choice for the task (with a value attached if one was pending)
     */
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

    /**
     * Updates the tracker state and answers reactive random-value requests.
     *
     * <p>First delegates to the superclass so the trackers update the runnable-task set. Then, for a
     * {@link JmcRuntimeEvent.Type#REACTIVE_EVENT_RANDOM_VALUE}, generates a random value of the
     * requested bit width and stashes it in {@link #randomValueMap} keyed by the requesting task, to
     * be delivered on that task's next scheduling choice.
     *
     * @param event the event that occurred
     * @throws HaltTaskException if the originating task must be halted
     * @throws HaltExecutionException if the current execution must be halted
     */
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

    /**
     * Records the current schedule for later replay.
     *
     * <p>Writes the seed to {@code replay_seed.txt} and the ordered list of choices to {@code
     * replay_trace.json}, both under {@link #reportPath}.
     *
     * @throws JmcCheckerException if writing the seed or trace files fails
     */
    @Override
    public void recordTrace() throws JmcCheckerException {
        String seedFilePath = Paths.get(this.reportPath, "replay_seed.txt").toString();
        String traceFilePath = Paths.get(this.reportPath, "replay_trace.json").toString();
        FileUtil.unsafeStoreToFile(seedFilePath, this.curTrace.getSeed() + "\n");
        FileUtil.storeTaskSchedule(traceFilePath, this.curTrace.getChoices());
    }

    /**
     * Replays a previously recorded trace.
     *
     * <p>Not yet implemented (the recorded trace is currently not consumed).
     *
     * @throws JmcCheckerException if replay fails
     */
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

    /**
     * An immutable-seed record of a single iteration's schedule: the RNG seed plus the ordered list
     * of scheduling choices made. Persisted by {@link #recordTrace()} for replay.
     */
    protected static class RandomSchedulingTrace {
        /** The RNG seed for this trace. */
        private final long seed;
        /** The scheduling choices made during the iteration, in order. */
        private final List<SchedulingChoice<?>> choices;

        /**
         * Constructs a new trace for the given seed with an empty choice list.
         *
         * @param seed the RNG seed for this trace
         */
        public RandomSchedulingTrace(long seed) {
            this.seed = seed;
            this.choices = new ArrayList<>();
        }

        /**
         * Appends a scheduling choice to this trace.
         *
         * @param choice the choice to append
         */
        public void addChoice(SchedulingChoice<?> choice) {
            choices.add(choice);
        }

        /**
         * Returns the RNG seed for this trace.
         *
         * @return the seed
         */
        public long getSeed() {
            return seed;
        }

        /**
         * Returns the ordered list of scheduling choices in this trace.
         *
         * @return the list of choices
         */
        public List<SchedulingChoice<?>> getChoices() {
            return choices;
        }
    }
}
