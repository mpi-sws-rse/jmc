package org.mpisws.jmc.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpisws.jmc.util.FileUtil;

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

    /*
     * ExtRandom is a custom random number generator that exposes the seed and mimics the behavior
     * of the default Random class.
     */
    protected static class ExtRandom extends Random {
        private AtomicLong seed;

        private static final long multiplier = 0x5DEECE66DL;
        private static final long addend = 0xBL;
        private static final long mask = (1L << 48) - 1;

        public ExtRandom(long seed) {
            super(seed);
        }

        private static long initialScramble(long seed) {
            return (seed ^ multiplier) & mask;
        }

        @Override
        public synchronized void setSeed(long seed) {
            super.setSeed(seed);
            this.seed = new AtomicLong(initialScramble(seed));
        }

        public Long getSeed() {
            return seed.get();
        }

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
