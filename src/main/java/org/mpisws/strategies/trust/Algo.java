package org.mpisws.strategies.trust;

import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.HaltTaskException;
import org.mpisws.runtime.SchedulingChoice;
import org.mpisws.util.files.FileUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains the core Trust algorithm implementation. (<a
 * href="https://doi.org/10.1145/3498711"></a>) We implement the recursive version described in the
 * paper and in the thesis. (<a
 * href="https://kluedo.ub.rptu.de/frontdoor/index/index/docId/7670"></a>)
 *
 * <p>This class contains, in addition to the execution graph and the algorithm, the auxiliary state
 * needed to enforce a specific task scheduling order.
 *
 * <p>Disclaimer!! This algorithm assumes that the programs are deterministic. Meaning, if you run a
 * task, you will receive the same sequence of events in that task.
 */
public class Algo {
    // The sequence of tasks to be scheduled. This is kept in sync with the execution graph that we
    // are currently visiting.
    private ArrayDeque<SchedulingChoice> guidingTaskSchedule;
    private ExecutionGraph executionGraph;
    private boolean isGuiding;
    private final ExplorationStack explorationStack;

    /** Creates a new instance of the Trust algorithm. */
    public Algo() {
        this.guidingTaskSchedule = null;
        this.isGuiding = false;
        this.executionGraph = new ExecutionGraph();
        this.explorationStack = new ExplorationStack();

        this.executionGraph.addEvent(Event.init());
    }

    /** Returns the next task to be scheduled according to the execution graph set in place. */
    public SchedulingChoice nextTask() {
        if (!isGuiding) {
            return null;
        }
        if (guidingTaskSchedule == null || guidingTaskSchedule.isEmpty()) {
            return null;
        }
        SchedulingChoice out = guidingTaskSchedule.pop();
        if (guidingTaskSchedule.isEmpty()) {
            isGuiding = false;
        }
        return out;
    }

    /**
     * Handles the visit with this event. Equivalent of running a single loop iteration of the Visit
     * method of the algorithm.
     *
     * <p>We assume that the updateEvent call is followed immediately by a yield call. Therefore, we
     * check the top of a guiding trace and raise exception if the scheduling choice is blocking.
     *
     * @param event A {@link Event} that is used to update the execution graph.
     */
    @SuppressWarnings({"checkstyle:MissingSwitchDefault", "checkstyle:LeftCurly"})
    public void updateEvent(Event event) throws HaltTaskException, HaltExecutionException {
        if (areWeGuiding()) {
            SchedulingChoice choice = guidingTaskSchedule.peek();
            if (choice.isBlockTask()) {
                throw new HaltTaskException(choice.getTaskId());
            } else if (choice.isBlockExecution()) {
                throw HaltExecutionException.error("Encountered a block label");
            }
        }

        switch (event.getType()) {
            case END:
                handleBot(event);
                break;
            case READ:
                handleRead(event);
                break;
            case WRITE:
                if (EventUtils.isLockAcquireWrite(event)) {
                    handleLockAcquireWrite(event);
                } else if (EventUtils.isLockReleaseWrite(event)) {
                    handleLockReleaseWrite(event);
                } else {
                    handleWrite(event);
                }
                break;
            case READ_EX:
                if (EventUtils.isLockAcquireRead(event)) {
                    handleLockAcquireRead(event);
                } else {
                    handleRead(event);
                }
                break;
            case WRITE_EX:
                handleWriteX(event);
                break;
            case LOCK_AWAIT:
                handleLockAwait(event);
                break;
            case NOOP:
                if (areWeGuiding()) {
                    return;
                }
                this.executionGraph.addEvent(event);
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

    /** Checks if we are guiding the execution. */
    private boolean areWeGuiding() {
        return isGuiding && guidingTaskSchedule != null && !guidingTaskSchedule.isEmpty();
    }

    private void resetWith(ExplorationStack.Item item) {
        // Reset based on the kind of item in the schedule
        ExecutionGraph newGraph = explorationStack.getGraph(item);
        if (newGraph != null) {
            executionGraph = newGraph;
        }
        switch (item.getType()) {
            case FRW:
                // Forward revisit of w -> r
                ExecutionGraphNode read = item.getEvent1();
                ExecutionGraphNode write = item.getEvent2();

                executionGraph.changeReadsFrom(read, write);
                executionGraph.restrictTo(read);
                guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
                break;
            case FWW:
                // Forward revisit of w -> w (alternative coherence placing)
                ExecutionGraphNode write1 = item.getEvent1();
                ExecutionGraphNode write2 = item.getEvent2();

                executionGraph.swapCoherency(write1, write2);
                // TODO: bug here. When restricting, we need to include the CO edges here.
                //  unlike with the rw revisit.
                executionGraph.restrictTo(write2);
                guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
                break;
            case BRR:
                guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
                break;
            case BWR:
                // Should not happen. We should have handled this in the resetIteration method.
                break;
        }
    }

    /**
     * Resets the iteration. This method is called at the end of each iteration of the algorithm.
     */
    public void resetIteration() {
        // Reset the task schedule and the execution graph.
        // Check if the top of the exploration stack is a backward revisit.
        // If so, copy the graph from the backward revisit and push all forward revisits to the
        // stack.
        if (!explorationStack.isEmpty() && explorationStack.peek().isBackwardRevisit()) {
            ExplorationStack.Item item = explorationStack.pop();
            if (item.getType() == ExplorationStack.ItemType.BWR) {
                ExecutionGraphNode write = item.getEvent1();
                ExecutionGraph restrictedGraph = item.getGraph();

                List<ExecutionGraphNode> alternativeWrites =
                        restrictedGraph.getCoherentPlacings(write);
                for (ExecutionGraphNode alternativeWrite : alternativeWrites) {
                    explorationStack.push(
                            ExplorationStack.Item.forwardWW(
                                    write, alternativeWrite, executionGraph));
                }
            }
        }
    }

    /**
     * Cleans up the execution graph and the task schedule. This method is called at the end of the
     * exploration.
     */
    public void teardown() {
        // Clean up the execution graph and the task schedule.
        this.executionGraph.clear();
        this.explorationStack.clear();
    }

    public List<Long> getSchedulableTasks() {
        // Get from execution graph
        return this.executionGraph.getUnblockedTasks().stream().map(Long::valueOf).toList();
    }

    private void handleError(Event event) {
        // Error events, halt the current execution.
        if (event.getType() == Event.Type.ERROR) {
            String message = event.getAttribute("message");
            throw HaltExecutionException.error(message);
        }
    }

    private void handleBot(Event event) {
        // End of the execution
        // No-op for now
        throw HaltExecutionException.ok();
    }

    private void handleRead(Event event) {
        if (areWeGuiding()) {
            return;
        }
        ExecutionGraphNode read = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());

        // TODO: If coMaxWrite is init (reading from a possibly uninitialized location). Write
        //      warning if flag is set
        // if (coMaxWrite.getEvent().isInit()) {
        //
        // }

        // Need to handle lock acquire reads
        // If the read is reading from a write of a lock acquire then we need to add a lock await
        // label after the read to block the thread.

        if (coMaxWrite.happensBefore(read)) {
            // Easy case. No concurrent write to revisit.
            executionGraph.setReadsFrom(read, coMaxWrite);
            return;
        }
        List<ExecutionGraphNode> alternativeWrites = executionGraph.getAlternativeWrites(read);
        // Set the reads-from relation
        executionGraph.setReadsFrom(read, coMaxWrite);
        if (alternativeWrites.isEmpty()) {
            // No alternative writes to revisit.
            return;
        }
        // We have alternative writes to revisit.
        for (ExecutionGraphNode alternativeWrite : alternativeWrites) {
            explorationStack.push(
                    ExplorationStack.Item.forwardRW(
                            read, alternativeWrite, this.executionGraph.clone()));
        }
    }

    private void handleWrite(Event event) {
        if (areWeGuiding()) {
            return;
        }
        // Add the write event to the execution graph
        ExecutionGraphNode write = executionGraph.addEvent(event);

        // Check for (w->w) coherent forward revisits (VisitCO)
        List<ExecutionGraphNode> concurrentWrites = executionGraph.getCoherentPlacings(write);
        executionGraph.trackCoherency(write);
        if (!concurrentWrites.isEmpty()) {
            // We have concurrent writes to revisit.
            // If flag is set, write race warning
            for (ExecutionGraphNode concurrentWrite : concurrentWrites) {
                explorationStack.push(
                        ExplorationStack.Item.forwardWW(write, concurrentWrite, executionGraph));
            }
        }

        // Check for (w->r) backward revisits
        // Find potential reads that need to be revisited
        List<ExecutionGraphNode> potentialReads = executionGraph.getPotentialReads(write);
        if (potentialReads.isEmpty()) {
            return;
        }
        List<BackwardRevisitView> revisitViews =
                potentialReads.stream().map((r) -> executionGraph.revisitView(write, r)).toList();

        revisitViews =
                revisitViews.stream().filter(BackwardRevisitView::isMaximalExtension).toList();

        for (BackwardRevisitView revisit : revisitViews) {
            explorationStack.push(
                    ExplorationStack.Item.backwardRevisit(
                            revisit.getWrite(), revisit.getRestrictedGraph()));
        }
    }

    private void handleWriteX(Event event) {
        if (areWeGuiding()) {
            return;
        }
        // Add the write event to the execution graph
        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);

        // There will not be any (w->w) forward revisits for exclusive writes.
        // Because all exclusive writes to the same location are totally ordered by the
        // happens-before

        // Check for (w->r) backward revisits
        // Find potential reads that need to be revisited
        // Additionally, we need to add a blocking label to the writes of the corresponding revisit
        // reads.
        // The only reason there are alternative reads to consider is due to two reads
        // reading from the same exclusive write which is inconsistent but the one-step
        // inconsistency is needed to ensure we explore the alternative ordering.
        List<ExecutionGraphNode> potentialReads = executionGraph.getPotentialReads(write);
        if (potentialReads.isEmpty()) {
            return;
        }
        List<BackwardRevisitView> revisitViews =
                potentialReads.stream().map((r) -> executionGraph.revisitView(write, r)).toList();

        revisitViews =
                revisitViews.stream().filter(BackwardRevisitView::isMaximalExtension).toList();

        for (BackwardRevisitView revisit : revisitViews) {
            // Block the tasks of the reads that need to be revisited
            executionGraph.addBlockingLabel(revisit.getRead().getEvent().getTaskId(), false);
            explorationStack.push(
                    ExplorationStack.Item.backwardRevisit(
                            revisit.getWrite(), revisit.getRestrictedGraph()));
        }
    }

    private void handleLockAcquireRead(Event event) {
        if (areWeGuiding()) {
            return;
        }

        ExecutionGraphNode read = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());
        // If reading from acquire write, then add a lock await label after the read
        if (EventUtils.isLockAcquireWrite(coMaxWrite.getEvent())) {
            executionGraph.addBlockingLabel(read.getEvent().getTaskId(), true);
        }

        // Check if there are alternative reads from the same write then block
        List<ExecutionGraphNode> alternativeReads = new ArrayList<>();
        for (Event.Key readKey : coMaxWrite.getSuccessors(Relation.ReadsFrom)) {
            try {
                alternativeReads.add(executionGraph.getEventNode(readKey));
            } catch (NoSuchEventException e) {
                throw new RuntimeException(e);
            }
        }
        if (alternativeReads.size() > 1) {
            executionGraph.addBlockingLabel(read.getEvent().getTaskId(), true);
        }

        // Find alternative lock reads to revisit
        List<LockBackwardRevisitView> alternativeWrites =
                executionGraph.getAlternativeLockRevisits(read);
        if (alternativeWrites.isEmpty()) {
            return;
        }
        alternativeWrites =
                alternativeWrites.stream().filter(LockBackwardRevisitView::isRevisitAble).toList();
        for (LockBackwardRevisitView alternativeWrite : alternativeWrites) {
            explorationStack.push(
                    ExplorationStack.Item.lockBackwardRevisit(
                            read,
                            alternativeWrite.getRevisitRead(),
                            alternativeWrite.getRestrictedGraph()));
        }
    }

    private void handleLockAcquireWrite(Event event) {
        if (areWeGuiding()) {
            return;
        }

        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);

        List<ExecutionGraphNode> alternateLockReads = executionGraph.getAlternativeLockReads(write);
        if (alternateLockReads.isEmpty()) {
            return;
        }
        for (ExecutionGraphNode read : alternateLockReads) {
            if (!EventUtils.isLockAcquireRead(read.getEvent())) {
                continue;
            }
            Long taskId = read.getEvent().getTaskId();
            if (!executionGraph.isTaskBlocked(taskId)) {
                executionGraph.addBlockingLabel(read.getEvent().getTaskId(), true);
            }
        }
    }

    private void handleLockReleaseWrite(Event event) {
        if (areWeGuiding()) {
            return;
        }

        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);

        List<ExecutionGraphNode> alternateLockReads = executionGraph.getAlternativeLockReads(write);
        if (alternateLockReads.isEmpty()) {
            return;
        }
        for (ExecutionGraphNode read : alternateLockReads) {
            Long taskId = read.getEvent().getTaskId();
            if (!executionGraph.isTaskBlocked(taskId)) {
                continue;
            }
            executionGraph.unBlockTask(taskId);
            executionGraph.changeReadsFrom(read, write);
        }
    }

    private void handleLockAwait(Event event) {}

    /**
     * Writes the execution graph to a file.
     *
     * @param filePath The path to the file to write the execution graph to.
     */
    public void writeExecutionGraphToFile(String filePath) {
        String executionGraphJson = executionGraph.toJsonString();
        FileUtil.unsafeStoreToFile(filePath, executionGraphJson);
    }
}
