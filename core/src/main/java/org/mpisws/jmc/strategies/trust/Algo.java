package org.mpisws.jmc.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.*;
import org.mpisws.jmc.util.files.FileUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private static Logger LOGGER = LogManager.getLogger(Algo.class);
    // The sequence of tasks to be scheduled. This is kept in sync with the execution graph that we
    // are currently visiting.
    private ArrayDeque<SchedulingChoiceWrapper> guidingTaskSchedule;
    private ExecutionGraph executionGraph;
    private boolean isGuiding;
    private final ExplorationStack explorationStack;

    private final LocationStore locationStore;

    /**
     * Creates a new instance of the Trust algorithm.
     */
    public Algo() {
        this.guidingTaskSchedule = null;
        this.isGuiding = false;
        this.executionGraph = new ExecutionGraph();
        this.explorationStack = new ExplorationStack();
        this.locationStore = new LocationStore();

        this.executionGraph.addEvent(Event.init());
    }

    /**
     * Returns the next task to be scheduled according to the execution graph set in place.
     */
    public SchedulingChoice<?> nextTask() {
        if (!isGuiding) {
            return null;
        }
        if (guidingTaskSchedule == null || guidingTaskSchedule.isEmpty()) {
            return null;
        }
        SchedulingChoice<?> out = guidingTaskSchedule.pop().choice();
        if (guidingTaskSchedule.isEmpty()) {
            isGuiding = false;
        }
        return out;
    }

    private void handleGuidedEvent(Event event) {
        SchedulingChoiceWrapper choiceW = guidingTaskSchedule.peek();
        SchedulingChoice<?> choice = choiceW.choice();
        if (choice.isBlockTask()) {
            throw new HaltTaskException(choice.getTaskId());
        } else if (choice.isBlockExecution()) {
            throw HaltExecutionException.error("Encountered a block label");
        } else if (choice.isEnd()) {
            // We have observed all the events in the guiding trace, pop the end event
            guidingTaskSchedule.pop();
            if (guidingTaskSchedule.isEmpty()) {
                isGuiding = false;
                LOGGER.debug("The guiding task schedule is empty");
            }
        }
        if (choiceW.hasLocation()) {
            Integer location = choiceW.location();
            if (event.getLocation() == null) {
                throw HaltExecutionException.error(
                        "Expected location with event but it contains none");
            }
            if (Objects.equals(event.getLocation(), location)) {
                return;
            }
            if (!locationStore.containsAlias(event.getLocation())) {
                locationStore.addAlias(location, event.getLocation());
            }
        }
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
        LOGGER.debug("Received event: {}", event);
        if (areWeGuiding()) {
            handleGuidedEvent(event);
            return;
        }

        // Need to assign the right location value to the event. Check aliases and update the event
        // location accordingly.
        if (event.getLocation() != null) {
            if (locationStore.containsAlias(event.getLocation())) {
                event.setLocation(locationStore.getAlias(event.getLocation()));
            } else {
                locationStore.addLocation(event.getLocation());
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
                handleNoop(event);
        }
        LOGGER.debug("Handled event: {}", event);
    }

    /**
     * Handles the NOOP event. This is a special case where we do not need to update the execution
     * graph.
     *
     * @param event The NOOP event.
     */
    public void handleNoop(Event event) {
        ExecutionGraphNode eventNode = this.executionGraph.addEvent(event);
        // Maintain total order among thread start events
        if (EventUtils.isThreadStart(event)) {
            this.executionGraph.trackThreadCreates(eventNode);
            if (event.getTaskId() != 0) { // Skip the main thread
                this.executionGraph.trackThreadStarts(eventNode);
            }
        } else if (EventUtils.isThreadJoin(event)) {
            this.executionGraph.trackThreadJoins(eventNode);
        }
    }

    /**
     * Initializes the iteration. This method is called at the beginning of each iteration of the
     * algorithm.
     *
     * @param iteration The iteration number.
     */
    public void initIteration(int iteration) {
        // Check if we are guiding the execution and construct the task schedule accordingly!
        if (iteration == 0) {
            LOGGER.debug("Initializing iteration");
            return;
        }

        // Check if the exploration stack is empty. If so, we are done with the exploration.
        if (explorationStack.isEmpty()) {
            LOGGER.debug("Exploration stack is empty. We are done with the exploration.");
            // We have reached the end of the exploration stack.
            // We should not be guiding the execution
            throw new HaltCheckerException();
        }

        /** Prepare guiding phase **/

        // Clear location aliases. By this point, we have replaced all the locations in the
        // execution graph with the latest ones.
        // TODO: need to check this properly
        locationStore.clearAliases();

        // We are guiding
        isGuiding = true;

        LOGGER.debug("Initializing the {}th iteration", iteration);
        findNextExplorationChoice();
    }

    /**
     * Checks if we are guiding the execution.
     */
    private boolean areWeGuiding() {
        return isGuiding && guidingTaskSchedule != null && !guidingTaskSchedule.isEmpty();
    }

    private void findNextExplorationChoice() {
        if (explorationStack.isEmpty()) {
            // This must not happen. We should have handled this in the resetIteration method.
            throw new RuntimeException( // TODO : We need to define a better exception for this case.
                    "Exploration stack is empty. We should have handled this in the resetIteration method.");
        }

        // The main loop of the procedure
        ArrayList<ExecutionGraphNode> nextGraphSchedule = new ArrayList<>();
        while (nextGraphSchedule.isEmpty()) {

            if (explorationStack.isEmpty()) {
                // We have reached the end of the exploration stack.
                throw new HaltCheckerException();
            }

            // Get the next exploration choice from the exploration stack.
            ExplorationStack.Item item = explorationStack.pop();

            // Check if the item is a backward revisit.
            if (item.isBackwardRevisit()) { // TODO : Is any backward revisit type allowed? or only BWR?
                processBWR(item);
                continue;
            }

            // Handle the forward revisit
            ExecutionGraph newGraph = explorationStack.getGraph(item);
            if (newGraph == null) {
                // It is not possible for an item to have a null graph. This must be a bug in the
                // exploration stack.
                throw new RuntimeException(  // TODO : We need to define a better exception for this case.
                        "The exploration stack item has a null graph. This must be a bug in the exploration stack.");
            } else {
                executionGraph = newGraph;
            }

            switch (item.getType()) {
                case FRW -> nextGraphSchedule = processFRW(item);
                case FWW -> nextGraphSchedule = processFWW(item);
                case FLW -> nextGraphSchedule = processFLW(item);
                default -> throw new RuntimeException(
                        "The exploration stack item has an invalid type. This must be a bug in the exploration stack.");
            }
        }

        LOGGER.debug("Found the SC graph");
        // The SC graph is found. We need to set the guiding task schedule.
        // TODO : To increase efficiency, we can use the topological sort which
        guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule(nextGraphSchedule));
    }

    // TODO : Remove this method
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
                executionGraph.restrict(read);
                if (!executionGraph.checkConsistencyAndTopologicallySort().isEmpty()) {
                    guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
                }
                break;
            case FWW:
                // Forward revisit of w -> w (alternative coherence placing)
                ExecutionGraphNode write1 = item.getEvent1();
                ExecutionGraphNode write2 = item.getEvent2();

                executionGraph.swapCoherency(write1, write2);
                executionGraph.restrict(write1);
                if (!executionGraph.checkConsistencyAndTopologicallySort().isEmpty()) {
                    guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
                }
                break;
            case BRR:
                if (!executionGraph.checkConsistencyAndTopologicallySort().isEmpty()) {
                    guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
                }
                break;
            case BWR:
                // Should not happen. We should have handled this in the resetIteration method.
                break;
            case FLW:
                // Forward revisit of w -> lw (max-co)
                ExecutionGraphNode w = item.getEvent1();
                // set the co
                executionGraph.trackCoherency(w);
                executionGraph.restrict(w);
                if (!executionGraph.checkConsistencyAndTopologicallySort().isEmpty()) {
                    guidingTaskSchedule = new ArrayDeque<>(executionGraph.getTaskSchedule());
                }
                break;
        }
    }

    // For debugging
    public void printGuidingTaskSchedule() {
        if (guidingTaskSchedule == null) {
            System.out.println("Guiding task schedule is null");
            return;
        }
        System.out.println("Guiding task schedule:");
        for (SchedulingChoiceWrapper choice : guidingTaskSchedule) {
            System.out.print(choice.choice().getTaskId() + " - ");
        }
        System.out.println();
    }

    // TODO: Remove this method
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

                if (!alternativeWrites.isEmpty()) {
                    for (int i = alternativeWrites.size() - 1; i >= 0; i--) {
                        explorationStack.push(
                                ExplorationStack.Item.forwardWW(
                                        write, alternativeWrites.get(i), restrictedGraph));
                    }
                }

                // Pushing the max-co write forward revisit
                explorationStack.push(
                        ExplorationStack.Item.forwardLW(write, restrictedGraph));
            }
        }
    }

    private void processBWR(ExplorationStack.Item item) {

        ExecutionGraphNode write = item.getEvent1();
        ExecutionGraph restrictedGraph = item.getGraph();

        List<ExecutionGraphNode> alternativeWrites =
                restrictedGraph.getCoherentPlacings(write);

        if (!alternativeWrites.isEmpty()) {
            for (int i = alternativeWrites.size() - 1; i >= 0; i--) {
                explorationStack.push(
                        ExplorationStack.Item.forwardWW(
                                write, alternativeWrites.get(i), restrictedGraph));
            }
        }

        explorationStack.push(
                ExplorationStack.Item.forwardLW(write, restrictedGraph));
    }

    private ArrayList<ExecutionGraphNode> processFRW(ExplorationStack.Item item) {
        // Forward revisit of w -> r
        ExecutionGraphNode read = item.getEvent1();
        ExecutionGraphNode write = item.getEvent2();

        executionGraph.changeReadsFrom(read, write);
        executionGraph.restrict(read);

        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    private ArrayList<ExecutionGraphNode> processFWW(ExplorationStack.Item item) {
        // Forward revisit of w -> w (alternative coherence placing)
        ExecutionGraphNode write1 = item.getEvent1();
        ExecutionGraphNode write2 = item.getEvent2();

        executionGraph.swapCoherency(write1, write2);
        executionGraph.restrict(write1);
        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    private ArrayList<ExecutionGraphNode> processFLW(ExplorationStack.Item item) {
        // Forward revisit of w -> lw (max-co)
        ExecutionGraphNode w = item.getEvent1();

        // set the co
        executionGraph.trackCoherency(w);
        executionGraph.restrict(w);
        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    /**
     * Cleans up the execution graph and the task schedule. This method is called at the end of the
     * exploration.
     */
    public void teardown() {
        // Clean up the execution graph and the task schedule.
        this.executionGraph.clear();
        this.explorationStack.clear();
        this.locationStore.clearAliases();
        this.locationStore.clear();
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
            // TODO :: For debugging
            LOGGER.debug("Read is before the coMaxWrite");
            LOGGER.debug("The coMaxWrite is " + coMaxWrite.getEvent());
            // Easy case. No concurrent write to revisit. [Note that this is an optimization for
            // sequential consistency model. If we are exploring relaxed memory models in the future,
            // we need to remove this optimization.]
            executionGraph.setReadsFrom(read, coMaxWrite);
            return;
        }
        List<ExecutionGraphNode> alternativeWrites = executionGraph.getAlternativeWrites(read);

        // Set the reads-from relation
        executionGraph.setReadsFrom(read, coMaxWrite);

        if (alternativeWrites.isEmpty()) {
            LOGGER.debug("No alternative writes to revisit");
            // No alternative writes to revisit.
            return;
        }
        // We have alternative writes to revisit.

        for (int i = alternativeWrites.size() - 1; i >= 0; i--) {
            explorationStack.push(
                    ExplorationStack.Item.forwardRW(
                            read, alternativeWrites.get(i), this.executionGraph));
        }
    }

    private void handleWrite(Event event) {
        if (areWeGuiding()) {
            return;
        }

        // Add the write event to the execution graph
        ExecutionGraphNode write = executionGraph.addEvent(event);

        /** Check for (w->w) coherent forward revisits **/
        List<ExecutionGraphNode> concurrentWrites = executionGraph.getCoherentPlacings(write);

        if (!concurrentWrites.isEmpty()) {
            LOGGER.debug("Found concurrent writes to revisit");

            // We have concurrent writes to revisit.
            // If flag is set, write race warning
            for (int i = concurrentWrites.size() - 1; i >= 0; i--) {
                explorationStack.push(
                        ExplorationStack.Item.forwardWW(write, concurrentWrites.get(i), executionGraph));
            }
        } else {
            LOGGER.debug("No concurrent writes to revisit");
        }

        /** Check for (w->r) backward revisits **/
        // Find potential reads that need to be revisited
        // TODO :: I'm not sure the way `getPotentialReads` method is ordering the reads is correct.
        List<ExecutionGraphNode> potentialReads = executionGraph.getPotentialReads(write);
        if (potentialReads.isEmpty()) {
            LOGGER.debug("No potential reads to revisit");
            // After batching the forward revisits, since there is no backward revisit, we need to
            // continue the exploration by adding the recently added write as the CO max.
            executionGraph.trackCoherency(write);
            return;
        }
        LOGGER.debug("Found potential reads to revisit");

        List<BackwardRevisitView> revisitViews =
                potentialReads.stream().map((r) -> executionGraph.revisitView(write, r)).toList();

        revisitViews =
                revisitViews.stream().filter(BackwardRevisitView::isMaximalExtension).toList();

        for (int i = revisitViews.size() - 1; i >= 0; i--) {
            explorationStack.push(
                    ExplorationStack.Item.backwardRevisit(
                            revisitViews.get(i).getWrite(), revisitViews.get(i).getRestrictedGraph()));
        }

        // After batching the backward and forward revisits, we need to continue the exploration by adding the recently
        // added write as the CO max.
        executionGraph.trackCoherency(write);
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

    private void handleLockAwait(Event event) {
    }

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
