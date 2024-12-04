package org.mpisws.strategies.trust;

import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.HaltTaskException;

import java.util.*;

/**
 * Contains the core Trust algorithm implementation. (<a
 * href="https://doi.org/10.1145/3498711"></a>) We implement the recursive version described in the
 * paper and in the thesis. (<a href="https://kluedo.ub.rptu.de/frontdoor/index/index/docId/7670"></a>)
 *
 * <p>This class contains, in addition to the execution graph and the algorithm, the auxiliary state
 * needed to enforce a specific task scheduling order.
 *
 * <p>Disclaimer!! This algorithm assumes that the programs are deterministic. Meaning, if you run a
 * task, you will receive the same sequence of events.
 */
public class Algo {
    // The sequence of tasks to be scheduled. This is kept in sync with the execution graph that we
    // are currently visiting.
    private ArrayDeque<Long> guidingTaskSchedule;
    private ExecutionGraph executionGraph;
    private boolean isGuiding;
    private final ExplorationStack explorationStack;


    /**
     * Creates a new instance of the Trust algorithm.
     */
    public Algo() {
        this.guidingTaskSchedule = null;
        this.isGuiding = false;
        this.executionGraph = new ExecutionGraph();
        this.explorationStack = new ExplorationStack();

        this.executionGraph.addEvent(Event.init());
    }

    /**
     * Returns the next task to be scheduled according to the execution graph set in place.
     */
    public Long nextTask() {
        if (!isGuiding) {
            return null;
        }
        if (guidingTaskSchedule == null || guidingTaskSchedule.isEmpty()) {
            return null;
        }
        return guidingTaskSchedule.pop();
    }

    /**
     * Handles the visit with this event. Equivalent of running a single loop iteration of the Visit
     * method of the algorithm.
     *
     * @param event A {@link Event} that is used to update the execution graph.
     */
    @SuppressWarnings({"checkstyle:MissingSwitchDefault", "checkstyle:LeftCurly"})
    public void updateEvent(Event event) throws HaltTaskException, HaltExecutionException {
        switch (event.getType()) {
            case END:
                handleBot(event);
                break;
            case READ:
                handleRead(event);
                break;
            case WRITE:
                handleWrite(event);
                break;
            case READ_EX:
                handleReadX(event);
                break;
            case WRITE_EX:
                handleWriteX(event);
                break;
            case LOCK_AWAIT:
                handleLockAwait(event);
                break;
        }
    }

    public void initIteration(int iteration) {
        // Check if we are guiding the execution and construct the task schedule accordingly!
        if (iteration == 0) {
            return;
        }

        if (explorationStack.isEmpty()) {
            // We have reached the end of the exploration stack.
            // We should not be guiding the execution
            throw new HaltCheckerException();
        }

        // We are guiding
        isGuiding = true;
        ExplorationStack.Item item = explorationStack.pop();
        resetWith(item);
    }

    private void resetWith(ExplorationStack.Item item) {
        // Reset based on the kind of item in the schedule
        if (!item.isBackwardRevisit()) {
            // Forward revisit (w -> r)
            ExecutionGraphNode write = item.getEvent1();
            ExecutionGraphNode read = item.getEvent2();

            executionGraph.setReadsFrom(write, read);
            executionGraph.restrictTo(read);
            guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
        } else {
            // TODO: complete backward revisit
        }
    }

    public void resetIteration() {
        // Reset the task schedule and the execution graph.
        // No-op for now.
    }

    public void teardown() {
        // Clean up the execution graph and the task schedule.
    }

    public List<Long> getSchedulableTasks() {
        // TODO: implement this method
        // Get from execution graph
        return new ArrayList<>();
    }

    private void handleError(Event event) {
    }

    private void handleBot(Event event) {
    }

    private void handleRead(Event event) {
    }

    private void handleWrite(Event event) {
    }

    private void handleReadX(Event event) {
    }

    private void handleWriteX(Event event) {
    }

    private void handleLockAwait(Event event) {
    }
}
