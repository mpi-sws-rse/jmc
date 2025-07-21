package org.mpisws.jmc.test.strategies;

import org.mpisws.jmc.annotations.JmcIgnoreInstrumentation;
import org.mpisws.jmc.runtime.HaltCheckerException;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpisws.jmc.strategies.TrackActiveTasksStrategy;

import java.util.*;

/**
 * A strategy that selects tasks based on a weighted random approach. Each task has a weight that
 * determines its likelihood of being selected. The weight decreases each time the task is selected.
 *
 * <p>Note: The JmcIgnoreInstrumentation annotation is used to indicate that this class should not
 * be instrumented by the JMC model checker. As it will be run in the context of the JMC model
 * checker. This is true of all custom strategies.
 */
@JmcIgnoreInstrumentation
public class WeightedRandomStrategy extends TrackActiveTasksStrategy {

    private int maxPerTask;
    private Random random;

    private Map<Long, Integer> taskWeights = new HashMap<>();

    public WeightedRandomStrategy(int maxPerTask) {
        this.maxPerTask = maxPerTask;
        this.random = new Random();
        this.taskWeights = new HashMap<>();

        // Remove this line when the nextTask method is implemented
        throw new RuntimeException("WeightedRandomStrategy: Constructor not implemented yet");
    }

    /**
     * Invoked each time the strategy receives an event. In most cases, the updateEvent method is
     * called right before nextTask is called. Since all events are of the format
     * JmcRuntime.updateEventAndYield(event).
     *
     * @param event The RuntimeEvent that occurred.
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) {
        // Handle the event as needed.
        super.updateEvent(event);

        Long taskId = event.getTaskId();

        if (!taskWeights.containsKey(taskId)) {
            taskWeights.put(taskId, maxPerTask);
        }
        Integer taskWeight = taskWeights.get(taskId);
        if (taskWeight > 0) {
            taskWeights.put(taskId, taskWeight - 1);
        }
    }

    /**
     * Invoked each time the strategy needs to select a task to execute. (On ever JmcRuntime.yield()
     * call)
     *
     * @return A SchedulingChoice representing the next task to execute.
     */
    @Override
    public SchedulingChoice<?> nextTask() {

        Set<Long> activeTasks = getActiveTasks();

        List<Integer> weights = new ArrayList<>();
        for (Long taskId : activeTasks) {
            Integer weight = taskWeights.getOrDefault(taskId, maxPerTask);
            weights.add(weight);
        }

        Long taskSelected = null;

        // Fill in the logic to select a task based on weights

        throw HaltCheckerException.error("WeightedRandomStrategy: nextTask not implemented yet");

        // return SchedulingChoice.task(taskSelected);
    }
}
