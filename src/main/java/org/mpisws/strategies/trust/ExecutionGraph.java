package org.mpisws.strategies.trust;

import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.SchedulingChoice;
import org.mpisws.util.aux.LamportVectorClock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

/**
 * Represents an execution graph.
 *
 * <p>Contains the exploration and all the relations defined according to the Trust algorithm. For
 * now this class implements the sequential consistency model. Which, in theory, could be extended
 * to other models.
 *
 * <p>Some terminology to understand the code
 *
 * <ul>
 *   <li>TO: Total order of events observed in this execution graph, in the order they were added
 *   <li>PO: Program order. A union of reads from partial order and the total order of events per
 *       task
 *   <li>RF: Reads from relation between reads and writes
 *   <li>CO: A coherency order between writes
 * </ul>
 */
public class ExecutionGraph {

    private static final Logger LOGGER = LogManager.getLogger(ExecutionGraph.class);

    // Events observed in this execution graph grouped by task. This is the PO order
    private List<List<ExecutionGraphNode>> taskEvents;

    // Tracking coherency order between writes to the same location. This is the CO order
    private final HashMap<Location, List<ExecutionGraphNode>> coherencyOrder;

    // All events in the execution graph. This is the TO order
    private List<ExecutionGraphNode> allEvents;

    /**
     * Initializes a new execution graph.
     */
    public ExecutionGraph() {
        this.allEvents = new ArrayList<>();
        this.coherencyOrder = new HashMap<>();
        this.taskEvents = new ArrayList<>();
    }

    /* Copy constructor */
    private ExecutionGraph(ExecutionGraph graph) {
        this.taskEvents = new ArrayList<>();
        for (List<ExecutionGraphNode> taskEvent : graph.taskEvents) {
            List<ExecutionGraphNode> newTaskEvent = new ArrayList<>();
            for (ExecutionGraphNode node : taskEvent) {
                newTaskEvent.add(node.clone());
            }
            this.taskEvents.add(newTaskEvent);
        }
        this.allEvents = new ArrayList<>();
        for (ExecutionGraphNode node : graph.allEvents) {
            if (node.getEvent().isInit()) {
                // Need to clone the init event, so far it has not been added to the task events
                this.allEvents.add(node.clone());
                continue;
            }
            Event.Key nodeKey = node.key();
            this.allEvents.add(
                    this.taskEvents
                            .get(nodeKey.getTaskId().intValue())
                            .get(nodeKey.getTimestamp()));
        }
        this.coherencyOrder = new HashMap<>();
        for (Location location : graph.coherencyOrder.keySet()) {
            List<ExecutionGraphNode> writes = graph.coherencyOrder.get(location);
            List<ExecutionGraphNode> newWrites = new ArrayList<>();
            for (ExecutionGraphNode write : writes) {
                if (write.getEvent().isInit()) {
                    newWrites.add(this.allEvents.get(0));
                    continue;
                }
                Event.Key nodeKey = write.key();
                newWrites.add(
                        this.taskEvents
                                .get(nodeKey.getTaskId().intValue())
                                .get(nodeKey.getTimestamp()));
            }
            this.coherencyOrder.put(location, newWrites);
        }
    }

    /**
     * Returns the list of task identifiers in the execution graph based on the topologically sorted
     * order.
     *
     * @return The list of task identifiers in the execution graph.
     */
    public List<SchedulingChoice> getTaskSchedule() {
        List<SchedulingChoice> taskSchedule = new ArrayList<>();
        for (Iterator<ExecutionGraphNode> it = iterator(); it.hasNext(); ) {
            ExecutionGraphNode node = it.next();
            if (node.getEvent().isInit()) {
                continue;
            }
            // If the event is a blocking label then add the relevant task to the schedule
            if (EventUtils.isBlockingLabel(node.getEvent())) {
                Long taskId = node.getEvent().getTaskId();
                if (taskId == null) {
                    taskSchedule.add(SchedulingChoice.blockExecution());
                } else {
                    taskSchedule.add(SchedulingChoice.blockTask(node.getEvent().getTaskId()));
                }
                continue;
            }
            // Adding 1 to the task ID since the task ID is 0-indexed inside Trust but 1-indexed in
            // JMC
            taskSchedule.add(SchedulingChoice.task(node.getEvent().getTaskId()+1));
        }
        return taskSchedule;
    }

    /**
     * Returns the list of task identifiers in the execution graph where a new event can be added.
     *
     * @return The list of task identifiers in the execution graph.
     */
    public ArrayList<Integer> getUnblockedTasks() {
        ArrayList<Integer> unblockedTasks = new ArrayList<>();
        for (int i = 0; i < taskEvents.size(); i++) {
            if (taskEvents.get(i).isEmpty()) {
                unblockedTasks.add(i);
            } else {
                ExecutionGraphNode lastNode = taskEvents.get(i).get(taskEvents.get(i).size() - 1);
                if (!EventUtils.isBlockingLabel(lastNode.getEvent())) {
                    unblockedTasks.add(i);
                }
            }
        }
        return unblockedTasks;
    }

    /**
     * Returns the index of the given node in the TO order.
     *
     * @param node The node to get the index of.
     * @return The index of the given node in the TO order (-1 if not found).
     */
    protected int getTOIndex(ExecutionGraphNode node) {
        // A slight optimization to get start from the max vector clock value. The assumption is
        // that is at least after this value in the TO.
        for (int i = node.getVectorClock().max(); i < allEvents.size(); i++) {
            if (allEvents.get(i) == node) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the given key in the TO order.
     *
     * @param key The key to get the index of.
     * @return The index of the given key in the TO order (-1 if not found).
     */
    protected int getTOIndex(Event.Key key) {
        for (int i = 0; i < allEvents.size(); i++) {
            if (allEvents.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Creates a clone of the execution graph.
     *
     * @return A clone of the execution graph.
     */
    public ExecutionGraph clone() {
        return new ExecutionGraph(this);
    }

    /**
     * Returns the event node with the given key.
     *
     * @param key The key of the event to get.
     * @return The event node with the given key.
     * @throws NoSuchEventException If the event with the given key is not found.
     */
    public ExecutionGraphNode getEventNode(Event.Key key) throws NoSuchEventException {
        if (key.getTaskId() == null || key.getTimestamp() == null) {
            // Init event
            return allEvents.get(0);
        }
        int taskId = key.getTaskId().intValue();
        Integer timestamp = key.getTimestamp();
        if (taskId >= taskEvents.size() || timestamp >= taskEvents.get(taskId).size()) {
            throw new NoSuchEventException(key);
        }
        return taskEvents.get(taskId).get(timestamp);
    }

    private ExecutionGraphNode unsafeGetEventNode(Event.Key key) {
        if (key.getTaskId() == null || key.getTimestamp() == null) {
            // Init event
            return allEvents.get(0);
        }
        int taskId = key.getTaskId().intValue();
        Integer timestamp = key.getTimestamp();
        return taskEvents.get(taskId).get(timestamp);
    }

    /**
     * Returns true if the execution graph contains the event with the given key.
     *
     * @param key The key of the event to check.
     * @return True if the execution graph contains the event with the given key.
     */
    public boolean contains(Event.Key key) {
        if (key.getTaskId() == null || key.getTimestamp() == null) {
            // Init event
            return true;
        }
        int taskID = key.getTaskId().intValue();
        Integer timestamp = key.getTimestamp();
        return taskID < taskEvents.size() && timestamp < taskEvents.get(taskID).size();
    }

    /**
     * Adds an event to the execution graph.
     *
     * @param event The event to add.
     * @return The node representing the added event.
     */
    public ExecutionGraphNode addEvent(Event event) {
        if (event.isInit()) {
            // Add the initial event to the TO order
            ExecutionGraphNode initialNode =
                    new ExecutionGraphNode(event, new LamportVectorClock(0));
            allEvents.add(initialNode);
            LOGGER.debug("Added initial event.");
            return initialNode;
        }

        // Track the event in the PO order (fetch the latest vector clock first and use that to
        // create a node)
        int task = Math.toIntExact(event.getTaskId());
        if (task >= taskEvents.size()) {
            // Add empty task events to accommodate for the new task
            for (int i = taskEvents.size(); i <= task; i++) {
                taskEvents.add(new ArrayList<>());
            }
        }

        LamportVectorClock vectorClock = new LamportVectorClock(taskEvents.size());
        // The last event in the PO order (initial event by default)
        ExecutionGraphNode lastNodePO = allEvents.get(0);
        if (!taskEvents.get(task).isEmpty()) {
            lastNodePO = taskEvents.get(task).get(taskEvents.get(task).size() - 1);
            vectorClock = lastNodePO.getVectorClock();
        }
        if (EventUtils.isBlockingLabel(lastNodePO.getEvent())) {
            throw new HaltCheckerException("A blocking label is followed by an event.");
        }
        ExecutionGraphNode node = new ExecutionGraphNode(event, vectorClock);

        // Set timestamp to task event size
        event.setTimestamp(taskEvents.get(task).size());
        LOGGER.debug("Adding event: {}", event.key().toString());
        taskEvents.get(task).add(node);
        // Add the event to the PO order
        lastNodePO.addEdge(node, Relation.ProgramOrder);
        // Track the event in the TO order
        allEvents.add(node);

        // Track event location in the coherency order but not the event itself
        // Meaning don't add the event in the coherency order
        Location location = event.getLocation();
        if (location != null && !coherencyOrder.containsKey(location)) {
            // If the location is not already tracked, add the initial event
            List<ExecutionGraphNode> newWrites = new ArrayList<>();
            newWrites.add(allEvents.get(0));
            coherencyOrder.put(location, newWrites);
        }

        return node;
    }

    /**
     * Tracks thread join events in the execution graph. Adds a thread join edge from the last event of the
     * joined task to the thread join event.
     *
     * @param node The node representing the thread join event.
     */
    public void trackThreadJoins(ExecutionGraphNode node) {
        if(!EventUtils.isThreadJoin(node.getEvent())) {
            // Silent return if the event is not a thread join
            return;
        }

        // Adding a thread edge from the last event of the joinedTask to this event
        // Affects porf and happens before
        int joinedTask = EventUtils.getJoinedTask(node.getEvent());
        ExecutionGraphNode lastEventJoinedTask = taskEvents.get(joinedTask).get(taskEvents.get(joinedTask).size() - 1);
        lastEventJoinedTask.addEdge(node, Relation.ThreadJoin);
    }

    /**
     * Tracks the thread starts in the execution graph as a total order
     *
     * <p>Internally, it uses a special location in the coherency Order to maintain the total order.
     * Additionally, the relation is part of _porf_ and is reflected in the happens before.</p>
     *
     * @param node The node representing the thread start event.
     */
    public void trackThreadStarts(ExecutionGraphNode node) {
        if(!EventUtils.isThreadStart(node.getEvent())) {
            // Silent return if the event is not a thread start
            return;
        }

        // Tracking thread starts in the coherency order with a special static location object.
        List<ExecutionGraphNode> threadStarts = coherencyOrder.get(EventFactory.ThreadLocation);
        ExecutionGraphNode lastThreadStart = threadStarts.get(threadStarts.size() - 1);
        lastThreadStart.addEdge(node, Relation.ThreadCreation);
        coherencyOrder.get(EventFactory.ThreadLocation).add(node);
    }

    /**
     * Adds a blocking label to the execution graph.
     *
     * @param taskId The task ID to add the blocking label for.
     */
    public void addBlockingLabel(Long taskId, boolean lock_await) {
        Event.Type eventType = Event.Type.BLOCK;
        if (lock_await) {
            eventType = Event.Type.LOCK_AWAIT;
        }
        Event event = new Event(taskId, null, eventType);
        int task = Math.toIntExact(event.getTaskId());
        if (task >= taskEvents.size()) {
            // Add empty task events to accommodate for the new task
            for (int i = taskEvents.size(); i <= task; i++) {
                taskEvents.add(new ArrayList<>());
            }
        }
        LamportVectorClock vectorClock = new LamportVectorClock(taskEvents.size());
        // The last event in the PO order (initial event by default)
        ExecutionGraphNode lastNodePO = allEvents.get(0);
        if (!taskEvents.get(task).isEmpty()) {
            lastNodePO = taskEvents.get(task).get(taskEvents.get(task).size() - 1);
            vectorClock = lastNodePO.getVectorClock();
        }
        ExecutionGraphNode node = new ExecutionGraphNode(event, vectorClock);
        lastNodePO.addEdge(node, Relation.ProgramOrder);

        // Set timestamp to task event size
        event.setTimestamp(taskEvents.get(task).size());
        taskEvents.get(task).add(node);
    }

    /**
     * Checks if the task with the given ID is blocked.
     *
     * @param taskId The task ID to check.
     * @return True if the task with the given ID is blocked.
     */
    public boolean isTaskBlocked(Long taskId) {
        if (taskId == null || taskId >= taskEvents.size()) {
            return false;
        }
        List<ExecutionGraphNode> curTaskEvents = taskEvents.get(Math.toIntExact(taskId));
        if (curTaskEvents.isEmpty()) {
            return false;
        }
        ExecutionGraphNode lastNode = curTaskEvents.get(curTaskEvents.size() - 1);
        return EventUtils.isBlockingLabel(lastNode.getEvent());
    }

    /**
     * Unblocks the task with the given ID.
     *
     * @param taskId The task ID to block.
     * @throws HaltCheckerException If the task ID is invalid.
     */
    public void unBlockTask(Long taskId) throws HaltCheckerException {
        if (taskId == null || taskId > taskEvents.size()) {
            throw new HaltCheckerException("Invalid Task ID.");
        }
        List<ExecutionGraphNode> curTaskEvents = taskEvents.get(Math.toIntExact(taskId));
        if (curTaskEvents.isEmpty()) {
            throw new HaltCheckerException("The task is not blocked.");
        }
        ExecutionGraphNode blockNode = curTaskEvents.get(curTaskEvents.size() - 1);
        if (blockNode.getEvent().getType() != Event.Type.LOCK_AWAIT) {
            throw new HaltCheckerException("The task cannot be unblocked.");
        }
        curTaskEvents.remove(curTaskEvents.size() - 1);
        ExecutionGraphNode last = curTaskEvents.get(curTaskEvents.size() - 1);
        last.removeEdge(blockNode, Relation.ProgramOrder);
    }

    /**
     * Returns the last write event to the given location.
     *
     * @param location The location to get the last write event for.
     * @return The last write event to the given location.
     */
    public ExecutionGraphNode getCoMax(Location location) {
        List<ExecutionGraphNode> writes = coherencyOrder.get(location);
        if (writes == null || writes.isEmpty()) {
            // No writes to the location, therefore return the initial event
            return allEvents.get(0);
        }
        return writes.get(writes.size() - 1);
    }

    /**
     * Returns the nodes that are not _porf_-before the given node except the last node in the
     * returned list. Assumes that the given nodes are ordered in CO order.
     *
     * @param node  The node to split before.
     * @param nodes The nodes to split.
     * @return The nodes that are not _porf_-before the given node.
     */
    private List<ExecutionGraphNode> splitNodesBefore(
            ExecutionGraphNode node, List<ExecutionGraphNode> nodes) {
        List<ExecutionGraphNode> result = new ArrayList<>();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            ExecutionGraphNode iterNode = nodes.get(i);
            if (!iterNode.happensBefore(node)) {
                result.add(iterNode);
            } else {
                // Add the one last write that is _porf_-before the read
                result.add(iterNode);
                break;
            }
        }
        return result;
    }

    /**
     * Returns the alternative writes (in reverse CO order) to the given read event.
     *
     * <p>All writes that are not _porf_-before the given read. (Tied to Sequential consistency
     * model) ecluding the CO max write.
     *
     * @param read The read event node.
     * @return The alternative writes to the given read event.
     */
    public List<ExecutionGraphNode> getAlternativeWrites(ExecutionGraphNode read) {
        Location location = read.getEvent().getLocation();
        List<ExecutionGraphNode> allWrites =
                coherencyOrder.get(location).subList(0, coherencyOrder.get(location).size() - 1);
        return splitNodesBefore(read, allWrites);
    }

    /**
     * Returns the alternative reads to the given write event.
     *
     * <p>All reads that are not _porf_-before the given write. Specifically looking for lock
     * acquire reads. In the search, the concurrent writes do not include lock acquire exclusive
     * writes.
     *
     * @param write The write event node.
     * @return The alternative reads to the given write event.
     */
    public List<ExecutionGraphNode> getAlternativeLockReads(ExecutionGraphNode write) {
        Location location = write.getEvent().getLocation();
        List<ExecutionGraphNode> allWrites =
                coherencyOrder.get(location).subList(0, coherencyOrder.get(location).size() - 1);
        List<ExecutionGraphNode> alternativeWrites =
                splitNodesBefore(
                        write,
                        allWrites.stream()
                                .filter((w) -> !EventUtils.isLockAcquireWrite(w.getEvent()))
                                .toList());
        List<ExecutionGraphNode> lockReads = new ArrayList<>();
        for (ExecutionGraphNode altWrite : alternativeWrites) {
            Set<Event.Key> readKeys = altWrite.getSuccessors(Relation.ReadsFrom);
            for (Event.Key readKey : readKeys) {
                try {
                    ExecutionGraphNode readNode = getEventNode(readKey);
                    if (EventUtils.isLockAcquireRead(readNode.getEvent())
                            && readNode.getEvent().getLocation() == location) {
                        lockReads.add(readNode);
                    }
                } catch (NoSuchEventException e) {
                    throw HaltExecutionException.error("The read event is not found.");
                }
            }
        }
        return lockReads;
    }

    /**
     * Returns the potential alternative writes to the given lock read.
     *
     * <p>Writes that other lock reads are reading from.
     *
     * @param read The write event node.
     * @return The potential writes to the given read event.
     */
    public List<LockBackwardRevisitView> getAlternativeLockRevisits(ExecutionGraphNode read) {
        Location location = read.getEvent().getLocation();
        List<ExecutionGraphNode> allWrites =
                coherencyOrder.get(location).subList(0, coherencyOrder.get(location).size() - 1);
        List<ExecutionGraphNode> alternativeWrites =
                splitNodesBefore(
                        read,
                        allWrites.stream()
                                .filter((w) -> !EventUtils.isLockAcquireWrite(w.getEvent()))
                                .toList());

        List<LockBackwardRevisitView> lockBackwardRevisitViews = new ArrayList<>();
        for (ExecutionGraphNode altWrite : alternativeWrites) {
            // A possible location for the current read to read-from.
            Set<Event.Key> readKeys = altWrite.getSuccessors(Relation.ReadsFrom);
            for (Event.Key readKey : readKeys) {
                try {
                    ExecutionGraphNode readNode = getEventNode(readKey);
                    if (EventUtils.isLockAcquireRead(readNode.getEvent())
                            && readNode.getEvent().getLocation() == location) {
                        // If there is another lock read to the same location from this write
                        lockBackwardRevisitViews.add(
                                new LockBackwardRevisitView(read, readNode, this));
                    }
                } catch (NoSuchEventException e) {
                    throw HaltExecutionException.error("The read event is not found.");
                }
            }
        }

        return lockBackwardRevisitViews;
    }

    /**
     * Returns the potential alternative reads to the given write event.
     *
     * <p>All reads that are not _porf_-before the given write.
     *
     * @param write The write event node.
     * @return The potential reads to the given write event.
     */
    public List<ExecutionGraphNode> getPotentialReads(ExecutionGraphNode write) {
        List<ExecutionGraphNode> otherWrites = coherencyOrder.get(write.getEvent().getLocation());
        List<ExecutionGraphNode> nonPorfWrites = splitNodesBefore(write, otherWrites);
        if (nonPorfWrites.isEmpty()) {
            // No writes after the given write event
            // Should not happen. There should at least be the init.
            throw HaltExecutionException.error("No writes after the given write event.");
        }
        nonPorfWrites.remove(nonPorfWrites.size() - 1);
        if (nonPorfWrites.isEmpty()) {
            // Easy case, no other reads to revisit
            return nonPorfWrites;
        }

        // Following the sequential consistency model, we only consider non-exclusive writes
        nonPorfWrites.removeIf((w) -> !EventUtils.isExclusiveWrite(w.getEvent()));

        List<ExecutionGraphNode> reads = new ArrayList<>();
        for (ExecutionGraphNode alternativeWrite : nonPorfWrites) {
            Set<Event.Key> readKeys = alternativeWrite.getSuccessors(Relation.ReadsFrom);
            for (Event.Key readKey : readKeys) {
                try {
                    reads.add(getEventNode(readKey));
                } catch (NoSuchEventException e) {
                    throw HaltExecutionException.error("The read event is not found.");
                }
            }
        }
        reads =
                reads.stream()
                        // Filter out reads that are _porf_-before the write
                        .filter((r) -> !r.happensBefore(write))
                        .toList();
        return reads;
    }

    /**
     * Constructs a backward revisit view of the ExecutionGraph.
     *
     * @param write The write event
     * @param read  The read event that the write needs to backward revisit
     * @return The backward revisit view of the ExecutionGraph
     */
    public BackwardRevisitView revisitView(ExecutionGraphNode write, ExecutionGraphNode read) {
        // Construct a restricted view of the graph
        BackwardRevisitView restrictedView = new BackwardRevisitView(this, read, write);
        int readTOIndex = getTOIndex(read);
        if (readTOIndex == -1) {
            throw new HaltCheckerException("The read event is not found in the TO order.");
        }

        for (int i = readTOIndex; i < allEvents.size(); i++) {
            ExecutionGraphNode node = allEvents.get(i);
            if (!node.happensBefore(write)) {
                restrictedView.removeNode(node.key());
            }
        }
        return restrictedView;
    }

    /**
     * Returns the writes to the given location.
     *
     * @param location The location to get the writes for.
     * @return The writes to the given location.
     */
    public List<ExecutionGraphNode> getWrites(Location location) {
        return coherencyOrder.get(location);
    }

    /**
     * Resets the coherency order between the given write events. The later write is added just
     * before the earlier write.
     *
     * <p>Invalidates the total order of events in the graph. The concern of fixing the total order
     * is passed to the calling function.
     *
     * @param write1 The first write event.
     * @param write2 The second write event.
     */
    public void swapCoherency(ExecutionGraphNode write1, ExecutionGraphNode write2) {
        // Update the coherency order
        Location location = write1.getEvent().getLocation();
        if (!write2.getEvent().getLocation().equals(location)) {
            throw new HaltCheckerException("The write events are not to the same location.");
        }

        List<ExecutionGraphNode> writes = coherencyOrder.get(location);

        int write1Index = writes.indexOf(write1);
        int write2Index = writes.indexOf(write2);

        ExecutionGraphNode laterWrite = write2;
        int earlierIndex = write1Index;
        int laterIndex = write2Index;
        if (write1Index > write2Index) {
            laterWrite = write1;
            earlierIndex = write2Index;
            laterIndex = write1Index;
        }

        // Insert later write just before the earlier write in the writes list while moving the rest
        // of the writes.
        writes.remove(laterIndex);
        writes.add(earlierIndex, laterWrite);

        // Update the edges
        for (ExecutionGraphNode write : writes) {
            // TODO: fix this. We have to remove specific edges in the case of the init node which has
            //  many co edges to different locations
            write.removeEdge(Relation.Coherency);
        }
        for (int i = 0; i < writes.size() - 1; i++) {
            writes.get(i).addEdge(writes.get(i + 1), Relation.Coherency);
        }
    }

    /**
     * Returns the coherency placings of the given write event under sequential consistency.
     *
     * <p>Writes that are not _porf_-before the given write event.
     *
     * @param write The write event.
     * @return The coherency placings of the given write event.
     */
    public List<ExecutionGraphNode> getCoherentPlacings(ExecutionGraphNode write) {
        if (EventUtils.isExclusiveWrite(write.getEvent())) {
            // Easy path, since the coMax will be PORF before this write.
            // Based on the assumption that there are no concurrent writes between an exclusive read
            // and an exclusive write.
            return new ArrayList<>();
        }
        List<ExecutionGraphNode> allWrites = coherencyOrder.get(write.getEvent().getLocation());
        List<ExecutionGraphNode> writesAfter = splitNodesBefore(write, allWrites);
        if (writesAfter.isEmpty()) {
            // Bug! There should at least be the init
            throw new HaltCheckerException("No writes after the given write event.");
        }
        writesAfter.remove(writesAfter.size() - 1);
        if (writesAfter.isEmpty()) {
            // No writes after the given write event
            return writesAfter;
        }
        // Remove exclusive writes
        // Following the sequential consistency model, we only consider non-exclusive writes
        // (referencing GenMC implementation)
        writesAfter =
                writesAfter.stream()
                        .filter((w) -> !EventUtils.isExclusiveWrite(w.getEvent()))
                        .toList();
        return writesAfter;
    }

    /**
     * Updates the reads from relation between the given read and write events.
     *
     * <p>Invalidates the total order and the vector clocks of events in the graph. The concern of
     * fixing the total order and the vector clocks is passed to the calling function.
     *
     * @param read  The read event.
     * @param write The write event.
     */
    public void changeReadsFrom(ExecutionGraphNode read, ExecutionGraphNode write) {
        Set<Event.Key> writes = read.getPredecessors(Relation.ReadsFrom);
        if (writes.size() != 1) {
            throw new HaltCheckerException("A read has more than one RF back edge.");
        }
        try {
            ExecutionGraphNode previousWrite = getEventNode(writes.iterator().next());
            previousWrite.removeEdge(read, Relation.ReadsFrom);
            write.addEdge(read, Relation.ReadsFrom);
        } catch (NoSuchEventException e) {
            throw new HaltCheckerException("The previous write event is not found.");
        }
    }

    /**
     * Sets the reads from relation between the given read and write events.
     *
     * <p>Does not validate if there is an existing reads-from edge to the corresponding read
     *
     * @param read  The read event.
     * @param write The write event.
     */
    public void setReadsFrom(ExecutionGraphNode read, ExecutionGraphNode write) {
        write.addEdge(read, Relation.ReadsFrom);
    }

    /**
     * Tracks the coherency order between the given write event and the previous write event to the
     * same location.
     *
     * @param write The write event.
     */
    public void trackCoherency(ExecutionGraphNode write) {
        Location location = write.getEvent().getLocation();
        if (!coherencyOrder.containsKey(location)) {
            List<ExecutionGraphNode> writes = new ArrayList<>();
            writes.add(allEvents.get(0));
            coherencyOrder.put(location, writes);
        }
        coherencyOrder.get(location).add(write);
        ExecutionGraphNode previousWrite = allEvents.get(0);
        if (coherencyOrder.get(location).size() > 1) {
            previousWrite =
                    coherencyOrder.get(location).get(coherencyOrder.get(location).size() - 2);
        }
        LOGGER.debug("Adding coherency edge between {} and {}",
                previousWrite.getEvent().key().toString(),
                write.getEvent().key().toString());
        previousWrite.addEdge(write, Relation.Coherency);
    }

    /**
     * Restricts the execution graph by removing the nodes with the given keys.
     *
     * @param keys The keys of the nodes to be removed
     */
    public void restrictByRemoving(Set<Event.Key> keys) {
        // First recreate task events
        List<List<ExecutionGraphNode>> newTaskEvents = new ArrayList<>();
        for (List<ExecutionGraphNode> taskEvent : taskEvents) {
            List<ExecutionGraphNode> newTaskEvent = new ArrayList<>();
            for (ExecutionGraphNode node : taskEvent) {
                if (!keys.contains(node.key())) {
                    newTaskEvent.add(node);
                }
            }
            newTaskEvents.add(newTaskEvent);
        }
        taskEvents = newTaskEvents;
        for (Location location : coherencyOrder.keySet()) {
            List<ExecutionGraphNode> writes = coherencyOrder.get(location);
            List<ExecutionGraphNode> newWrites = new ArrayList<>();
            for (ExecutionGraphNode write : writes) {
                if (!keys.contains(write.key())) {
                    newWrites.add(write);
                }
            }
            coherencyOrder.put(location, newWrites);
        }
        List<ExecutionGraphNode> newAllEvents = new ArrayList<>();
        for (ExecutionGraphNode node : allEvents) {
            if (!keys.contains(node.key())) {
                newAllEvents.add(node);
            }
        }

        allEvents = newAllEvents;

        // Now remove dangling edges using the total order
        for (ExecutionGraphNode node : allEvents) {
            Set<Event.Key> successors = node.getAllSuccessors();
            for (Event.Key successor : successors) {
                if (keys.contains(successor)) {
                    node.removeAllEdgesTo(successor);
                }
            }
            Set<Event.Key> predecessors = node.getAllPredecessors();
            for (Event.Key predecessor : predecessors) {
                if (keys.contains(predecessor)) {
                    node.removeAllEdgesFrom(predecessor);
                }
            }
        }

        // Note, apparently there won't be any dangling reads. Need to verify this.

        // Recompute the vector clocks.
        recomputeVectorClocks();
    }

    /**
     * Recomputes the vector clocks of all nodes in the execution graph.
     */
    public void recomputeVectorClocks() {
        for (Iterator<ExecutionGraphNode> it = iterator(); it.hasNext(); ) {
            ExecutionGraphNode iterNode = it.next();
            if (iterNode.getAllPredecessors().isEmpty()) {
                // This is the init node, safely continue
                continue;
            }
            Set<Event.Key> poPredecessors = iterNode.getPredecessors(Relation.ProgramOrder);
            if (poPredecessors.size() != 1) {
                throw new HaltCheckerException("A node has more than one or no PO predecessor");
            }
            try {
                ExecutionGraphNode lastPONode = getEventNode(poPredecessors.iterator().next());
                iterNode.recomputeVectorClock(lastPONode, this::getEventNode);
            } catch (NoSuchEventException e) {
                throw new HaltCheckerException(e.getMessage());
            }
        }
    }

    /**
     * Restricts the execution graph to the given node. Meaning, all events that are not in the
     * causal prefix of the given node are removed.
     *
     * <p>Recompute the vector clocks of all nodes and delete those nodes that are not
     * happens-before the given node.
     *
     * @param restrictingNode The node to restrict to.
     */
    public void restrictTo(ExecutionGraphNode restrictingNode) {
        // First recompute vector clocks (because, prior to restrict we are assuming that the graph
        // has been modified)
        recomputeVectorClocks();

        // Update Task Events while tracking locations
        Set<Location> locationsToKeep = new HashSet<>();
        LamportVectorClock restrictingVectorClock = restrictingNode.getVectorClock();
        List<List<ExecutionGraphNode>> newTaskEvents = new ArrayList<>();
        for (int i = 0; i < taskEvents.size(); i++) {
            List<ExecutionGraphNode> newTaskEvent = new ArrayList<>();
            if (i > restrictingVectorClock.getSize()) {
                // This task has no events in the causal prefix of the restricting node
                newTaskEvents.add(newTaskEvent);
                continue;
            }
            for (ExecutionGraphNode iterNode : taskEvents.get(i)) {
                if (iterNode.getVectorClock().happensBefore(restrictingVectorClock)) {
                    newTaskEvent.add(iterNode);
                    Location eventLocation = iterNode.getEvent().getLocation();
                    if (eventLocation != null) {
                        locationsToKeep.add(eventLocation);
                    }
                }
                if (restrictingVectorClock.happensBefore(iterNode.getVectorClock())) {
                    // We have gone past in the TO. By definition, there are no other nodes that we
                    // need to include.
                    break;
                }
            }
            newTaskEvents.add(newTaskEvent);
        }
        taskEvents = newTaskEvents;

        // Update Total Order Events
        List<ExecutionGraphNode> newAllEvents = new ArrayList<>();
        for (ExecutionGraphNode iterNode : allEvents) {
            if (iterNode.happensBefore(restrictingNode)) {
                newAllEvents.add(iterNode);
            }
            if (restrictingNode.happensBefore(iterNode)) {
                // We have gone past in the TO. By definition, there are no other nodes that we need
                // to include.
                break;
            }
        }
        allEvents = newAllEvents;

        // Remove nodes from coherency tracking
        coherencyOrder.entrySet().removeIf(entry -> !locationsToKeep.contains(entry.getKey()));
        for (Map.Entry<Location, List<ExecutionGraphNode>> entry : coherencyOrder.entrySet()) {
            List<ExecutionGraphNode> writes = entry.getValue();
            List<ExecutionGraphNode> newWrites = new ArrayList<>();
            for (ExecutionGraphNode write : writes) {
                if (write.happensBefore(restrictingNode)) {
                    newWrites.add(write);
                }
            }
            entry.setValue(newWrites);
        }
    }

    /**
     * Returns an iterator walking through the nodes in a topological sort order.
     */
    public Iterator<ExecutionGraphNode> iterator() {
        return new TopologicalIterator(this);
    }

    public boolean isConsistent() {
        // TODO: Implement this method
        return true;
    }

    /**
     * Returns true if the graph contains only the initial event.
     */
    public boolean isEmpty() {
        return allEvents.size() == 1 && allEvents.get(0).getEvent().isInit();
    }

    /**
     * Clears the execution graph.
     */
    public void clear() {
        allEvents.clear();
        coherencyOrder.clear();
        taskEvents.clear();
    }

    public String toJsonString() {
        JsonObject nodes = new JsonObject();
        for (ExecutionGraphNode node : allEvents) {
            nodes.add(node.key().toString(), node.toJson());
        }
        JsonObject gson = new JsonObject();
        gson.add("nodes", nodes);
        return gson.toString();
    }

    /**
     * Returns an iterator that iterates over the nodes in the graph in topological order.
     *
     * <p>Note: can be improved by using DFS to do the ordering
     *
     * <p>Does not validate if the graph has cycles!
     */
    public static class TopologicalIterator implements Iterator<ExecutionGraphNode> {
        private final Queue<ExecutionGraphNode> queue;
        private final Set<Event.Key> visited;
        private final ExecutionGraph graph;

        /**
         * Initializes a new topological iterator for the given graph.
         *
         * @param graph The graph to iterate over.
         */
        public TopologicalIterator(ExecutionGraph graph) {
            this.queue = new LinkedList<>();
            this.visited = new HashSet<>();
            this.graph = graph;
            if (!graph.isEmpty()) {
                queue.add(graph.allEvents.get(0));
            }
        }

        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }

        @Override
        public ExecutionGraphNode next() {
            ExecutionGraphNode node = queue.poll();
            if (node == null) {
                throw new NoSuchElementException();
            }
            try {
                visited.add(node.key());
                for (Event.Key child :
                        node.getAllSuccessors().stream()
                                .sorted(Comparator.comparingLong(Event.Key::getTaskId))
                                .toList()) {
                    ExecutionGraphNode childNode = graph.getEventNode(child);
                    if (visited.containsAll(childNode.getAllPredecessors())) {
                        queue.add(childNode);
                    }
                }
            } catch (NoSuchEventException ignored) {
                // Should not be possible technically
            }
            return node;
        }
    }
}
