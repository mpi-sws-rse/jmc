package org.mpisws.jmc.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.SchedulingChoice;

import java.util.HashMap;
import java.util.Random;
import java.util.Set;

/**
 * A random scheduling strategy that selects the next thread to be scheduled randomly.
 */
public class RandomSchedulingStrategy extends TrackActiveTasksStrategy {

    private static final Logger LOGGER = LogManager.getLogger(RandomSchedulingStrategy.class);

    private final ExtRandom random;
    private final HashMap<Long, Integer> randomValueMap = new HashMap<>();

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    public RandomSchedulingStrategy(Long seed) {
        this.random = new ExtRandom(seed);
        this.randomValueMap.clear();
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
            int randomValue = randomValueMap.get(taskToSchedule);
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


    private class ExtRandom extends Random {
        public ExtRandom() {
            super();
        }

        public ExtRandom(long seed) {
            super(seed);
        }

        @Override
        public int next(int bits) {
            return super.next(bits);
        }
    }
}
