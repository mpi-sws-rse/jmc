package org.mpisws.strategies.trust;

import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.HaltTaskException;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.strategies.TrackActiveTasksStrategy;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class TrustStrategy extends TrackActiveTasksStrategy {

    private final Algo algoInstance;
    private final SchedulingPolicy policy;
    private final Random random;

    public TrustStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO);
    }

    public TrustStrategy(Long randomSeed, SchedulingPolicy policy) {
        super(List.of(new TrackTasks()));
        this.random = new Random(randomSeed);
        this.algoInstance = new Algo();
        this.policy = policy;
    }

    @Override
    public void initIteration(int iteration) {
        super.initIteration(iteration);
        algoInstance.initIteration(iteration);
    }

    @Override
    public Long nextTask() {
        // If the algorithm has a task to execute, return it
        Long nextTask = algoInstance.nextTask();
        if (nextTask != null) {
            return nextTask;
        }

        // Otherwise, return an active, schedule-able task based on the policy
        Set<Long> activeTasks = getActiveTasks();
        List<Long> activeScheduleAbleTasks = algoInstance.getSchedulableTasks().stream()
                .filter(activeTasks::contains)
                .toList();

        // If the policy is FIFO, return the first active, schedule-able task
        if (policy == SchedulingPolicy.FIFO) {
            return activeScheduleAbleTasks.isEmpty() ? null : activeScheduleAbleTasks.get(0);
        }

        // If the policy is RANDOM, return a random active, schedule-able task
        int size = activeScheduleAbleTasks.size();
        if (size == 0) {
            return null;
        }
        return activeScheduleAbleTasks.get(random.nextInt(size));
    }

    @Override
    public void updateEvent(RuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        List<Event> trustEvents = EventFactory.fromRuntimeEvent(event);
        for (Event e : trustEvents) {
            algoInstance.updateEvent(e);
        }
    }

    @Override
    public void resetIteration() {
        super.resetIteration();
        algoInstance.resetIteration();
    }

    @Override
    public void teardown() {
        super.teardown();
        algoInstance.teardown();
    }

    public enum SchedulingPolicy {
        FIFO, RANDOM
    }
}
