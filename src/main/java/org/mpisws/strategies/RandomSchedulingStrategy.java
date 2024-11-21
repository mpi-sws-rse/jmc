package org.mpisws.strategies;

import java.util.Random;
import java.util.Set;

/** A random scheduling strategy that selects the next thread to be scheduled randomly. */
public class RandomSchedulingStrategy extends TrackActiveThreadsStrategy
        implements SchedulingStrategy {

    private final Random random;

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public RandomSchedulingStrategy(Long seed) {
        this.random = new Random(seed);
    }

    @Override
    public Long nextTask() {
        Set<Long> activeThreads = getActiveThreads();
        if (activeThreads.isEmpty()) {
            return null;
        }
        int index = random.nextInt(activeThreads.size());
        return (Long) activeThreads.toArray()[index];
    }
}
