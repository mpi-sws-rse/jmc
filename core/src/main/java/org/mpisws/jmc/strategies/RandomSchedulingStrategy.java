package org.mpisws.jmc.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.SchedulingChoice;

import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A random scheduling strategy that selects the next thread to be scheduled randomly.
 */
public class RandomSchedulingStrategy extends TrackActiveTasksStrategy {

    private static final Logger LOGGER = LogManager.getLogger(RandomSchedulingStrategy.class);

    private final ExtRandom random;
    private final HashMap<Long, Integer> randomValueMap;

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public RandomSchedulingStrategy(Long seed) {
        this.random = new ExtRandom(seed);
        this.randomValueMap = new HashMap<>();
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) throws HaltExecutionException {
        super.initIteration(iteration, report);
        report.setReplaySeed(random.getSeed());
        LOGGER.info("Seed for iteration {} is {}", iteration, random.getSeed());
        randomValueMap.clear();
    }

    /**
     * Returns the next task to be scheduled. The task is picked randomly from the set of active
     * tasks.
     *
     * @return the next task to be scheduled
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        Set<Long> activeThreads = getActiveTasks();
        if (activeThreads.isEmpty()) {
            return null;
        }
        if (activeThreads.size() == 1) {
            return SchedulingChoice.task((Long) activeThreads.toArray()[0]);
        }
        int index = random.nextInt(activeThreads.size());
        Long taskToSchedule = (Long) activeThreads.toArray()[index];
        if (randomValueMap.containsKey(taskToSchedule)) {
            int randomValue = randomValueMap.remove(taskToSchedule);
            LOGGER.debug("Using cached random value {} for task {}", randomValue, taskToSchedule);
            return SchedulingChoice.task(taskToSchedule, randomValue);
        }
        return SchedulingChoice.task(taskToSchedule);
    }

    // Keep track of reactive events that need a return value
    @Override
    public void updateEvent(RuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        if (event.getType() == RuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE) {
            Long taskId = event.getTaskId();
            Integer bits = (Integer) event.getParam("bits");
            int randomValue = random.next(bits);
            randomValueMap.put(taskId, randomValue);
            LOGGER.debug("Generated random value {} for task {}", randomValue, taskId);
        }
    }

    /*
     * ExtRandom is a custom random number generator that exposes the seed and mimics the behavior
     * of the default Random class.
     */
    private static class ExtRandom extends Random {
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
        public void setSeed(long seed) {
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
}
