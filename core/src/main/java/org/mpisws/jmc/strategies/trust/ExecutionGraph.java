package org.mpisws.jmc.strategies.trust;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltCheckerException;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpisws.jmc.util.aux.LamportVectorClock;

import java.util.*;
import java.util.stream.Collectors;

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
    private final List<List<ExecutionGraphNode>> taskEvents;

    // Tracking coherency order between writes to the same location. This is the CO order
    private final HashMap<Integer, List<ExecutionGraphNode>> coherencyOrder;

    // All events in the execution graph. This is the TO order
    private List<ExecutionGraphNode> allEvents;

    private final HashMap<Integer, List<Long>> blockedLocks;

    /** Initializes a new execution graph. */
    public ExecutionGraph() {
        this.allEvents = new ArrayList<>();
        this.coherencyOrder = new HashMap<>();
        this.taskEvents = new ArrayList<>();
        this.blockedLocks = new HashMap<>();
    }

    /* Copy constructor */
    private ExecutionGraph(ExecutionGraph graph) {
        this.taskEvents = new ArrayList<>();
        for (List<ExecutionGraphNode> taskEvent : graph.taskEvents) {
            List<ExecutionGraphNode> newTaskEvent = new ArrayList<>();
            for (ExecutionGraphNode node : taskEvent) {
                if (EventUtils.isBlockingLabel(node.getEvent())) {
                    // We ignore blocking labels when revisiting
                    // And also remove the edge pointing to the blocking label
                    newTaskEvent
                            .get(newTaskEvent.size() - 1)
                            .removeEdgeTo(node.key(), Relation.ProgramOrder);
                    continue;
                }
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
        for (Integer location : graph.coherencyOrder.keySet()) {
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

        // When we clone, we forget about this.
        // It's only used for the forward revisits and
        // in the backward revisits, we ignore it.
        // Start fresh
        this.blockedLocks = new HashMap<>();
    }

    /**
     * Generate a task Schedule from a given sorted list of event nodes.
     *
     * <p>Note that the generated Schedule involves tasks that are 1-indexed and
     * The trust ExecutionGraph has tasks that are 0-indexed.</p>
     *
     * @param taskEvents A sorted list of event nodes
     * @return A list of SchedulingChoiceWrappers.
     */
    public static List<SchedulingChoiceWrapper> getTaskSchedule(List<ExecutionGraphNode> taskEvents) {
        List<SchedulingChoiceWrapper> result = new ArrayList<>();
        taskEvents.remove(0); // Remove the init event
        taskEvents.remove(0); // Remove the first event of the main thread

        Integer oldLocation = null;
        for (int i = 0; i < taskEvents.size(); i++) {
            ExecutionGraphNode node = taskEvents.get(i);
            Integer newLocation = node.getEvent().getLocation();
            // If the event is a blocking label then add the relevant task to the schedule
            if (EventUtils.isBlockingLabel(node.getEvent())) {
                Long taskId = node.getEvent().getTaskId();
                if (taskId == null) {
                    result.add(
                            new SchedulingChoiceWrapper(
                                    SchedulingChoice.blockExecution(), oldLocation));
                } else {
                    result.add(
                            new SchedulingChoiceWrapper(
                                    SchedulingChoice.blockTask(node.getEvent().getTaskId()),
                                    oldLocation));
                }
            } else if (EventUtils.isThreadStart(node.getEvent())) {
                result.add(
                        new SchedulingChoiceWrapper(
                                SchedulingChoice.task(EventUtils.getStartedBy(node.getEvent()) + 1),
                                oldLocation));
            } else if (EventUtils.isLockAcquireRead(node.getEvent())) {
                result.add(
                        new SchedulingChoiceWrapper(
                                SchedulingChoice.task(node.getEvent().getTaskId() + 1),
                                oldLocation));
                // We skip the lock acquire write since the two events are added for a single
                // runtime event
                i++;
            } else if (EventUtils.isThreadJoin(node.getEvent())) {
                // If we are scheduling a thread join,
                // we duplicate the task ID. since each join in trust is two separate events in the
                // runtime. Join request and join completion.
                Long taskId = node.getEvent().getTaskId() + 1;
                result.add(new SchedulingChoiceWrapper(SchedulingChoice.task(taskId), oldLocation));
                oldLocation = newLocation;
                newLocation = null;
                result.add(new SchedulingChoiceWrapper(SchedulingChoice.task(taskId), oldLocation));
            } else {
                // Adding 1 to the task ID since the task ID is 0-indexed inside Trust but 1-indexed
                // in JMC
                Long taskId = node.getEvent().getTaskId() + 1;
                result.add(new SchedulingChoiceWrapper(SchedulingChoice.task(taskId), oldLocation));
            }
            oldLocation = newLocation;
        }
        result.add(new SchedulingChoiceWrapper(SchedulingChoice.end(), oldLocation));
        return result;
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
            throw HaltCheckerException.error("A blocking label is followed by an event.");
        } else if (EventUtils.isThreadFinish(lastNodePO.getEvent())) {
            throw HaltCheckerException.error("A thread finish label is followed by an event.");
        }
        ExecutionGraphNode node = new ExecutionGraphNode(event, vectorClock);

        // Set timestamp to task event size
        event.setTimestamp(taskEvents.get(task).size());
        event.setToStamp(allEvents.size());
        LOGGER.debug("Adding event: {}", event.key().toString());
        taskEvents.get(task).add(node);
        // Add the event to the PO order
        lastNodePO.addEdge(node, Relation.ProgramOrder);
        // Track the event in the TO order
        allEvents.add(node);

        // Track event location in the coherency order but not the event itself
        // Meaning don't add the event in the coherency order
        Integer location = event.getLocation();
        if (location != null && !coherencyOrder.containsKey(location)) {
            // If the location is not already tracked, add the initial event
            List<ExecutionGraphNode> newWrites = new ArrayList<>();
            newWrites.add(allEvents.get(0));
            coherencyOrder.put(location, newWrites);
        }

        return node;
    }

    /**
     * Tracks thread join events in the execution graph. Adds a thread join edge from the last event
     * of the joined task to the thread join event.
     *
     * @param node The node representing the thread join event.
     */
    public void trackThreadJoins(ExecutionGraphNode node) {
        if (!EventUtils.isThreadJoin(node.getEvent())) {
            // Silent return if the event is not a thread join
            return;
        }

        // Adding a thread edge from the last event of the joinedTask to this event
        // Affects porf and happens before
        int joinedTask = EventUtils.getJoinedTask(node.getEvent());
        ExecutionGraphNode lastEventJoinedTask =
                taskEvents.get(joinedTask).get(taskEvents.get(joinedTask).size() - 1);
        lastEventJoinedTask.addEdge(node, Relation.ThreadJoin);
    }

    /**
     * Tracks the thread starts in the execution graph as a total order
     *
     * <p>Internally, it uses a special location in the coherency Order to maintain the total order.
     * Additionally, the relation is part of _porf_ and is reflected in the happens before.
     *
     * @param node The node representing the thread start event.
     */
    public void trackThreadCreates(ExecutionGraphNode node) {
        if (!EventUtils.isThreadStart(node.getEvent())) {
            // Silent return if the event is not a thread start
            return;
        }

        // Tracking thread starts in the coherency order with a special static location object.
        List<ExecutionGraphNode> threadStarts = coherencyOrder.get(LocationStore.ThreadLocation);
        ExecutionGraphNode lastThreadStart = threadStarts.get(threadStarts.size() - 1);
        lastThreadStart.addEdge(node, Relation.ThreadCreation);
        coherencyOrder.get(LocationStore.ThreadLocation).add(node);
    }

    public void trackThreadStarts(ExecutionGraphNode node) {
        if (!EventUtils.isThreadStart(node.getEvent())) {
            // Silent return if the event is not a thread start
            return;
        }

        // Adding a thread edge from the last event of the started task to this event
        // Affects porf and happens before
        Long startedBy = EventUtils.getStartedBy(node.getEvent());
        if (startedBy == null) {
            // No any ThreadStart event can be started by null. It is a bug in the code.
            throw new RuntimeException( // TODO : Replace with better exception
                    "The event is not started by any task.");
        }

        int startedByTask = Math.toIntExact(startedBy);
        ExecutionGraphNode lastEventStartedBy =
                taskEvents.get(startedByTask).get(taskEvents.get(startedByTask).size() - 1);
        lastEventStartedBy.addEdge(node, Relation.ThreadStart);
    }

    /**
     * Adds a blocking label to the execution graph.
     *
     * @param taskId The task ID to add the blocking label for.
     */
    public void addBlockingLabel(Long taskId) {
        Event.Type eventType = Event.Type.BLOCK;
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
            throw HaltCheckerException.error("Invalid Task ID.");
        }
        List<ExecutionGraphNode> curTaskEvents = taskEvents.get(Math.toIntExact(taskId));
        if (curTaskEvents.isEmpty()) {
            throw HaltCheckerException.error("The task is not blocked.");
        }
        ExecutionGraphNode blockNode = curTaskEvents.get(curTaskEvents.size() - 1);
        if (blockNode.getEvent().getType() != Event.Type.BLOCK) {
            throw HaltCheckerException.error("The task cannot be unblocked.");
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
    public ExecutionGraphNode getCoMax(Integer location) {
        List<ExecutionGraphNode> writes = coherencyOrder.get(location);
        if (writes == null || writes.isEmpty()) {
            // No writes to the location, therefore return the initial event
            return allEvents.get(0);
        }
        return writes.get(writes.size() - 1);
    }

    /**
     * Returns the nodes that are not _porf_-before the given node except the last node in the
     * returned list. Assumes that the given nodes are ordered in reverse CO order.
     *
     * @param node The node to split before.
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
        Integer location = read.getEvent().getLocation();
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
        Integer location = write.getEvent().getLocation();
        List<ExecutionGraphNode> allWrites = new ArrayList<>();
        for (int i = coherencyOrder.get(location).size() - 1; i >= 0; i--) {
            ExecutionGraphNode otherWrite = coherencyOrder.get(location).get(i);
            if (EventUtils.isFinalLockWrite(otherWrite.getEvent())) {
                break;
            }
            allWrites.add(otherWrite);
        }
        Collections.reverse(allWrites);
        List<ExecutionGraphNode> alternativeWrites = splitNodesBefore(write, allWrites);

        List<ExecutionGraphNode> lockReads = new ArrayList<>();
        for (ExecutionGraphNode altWrite : alternativeWrites) {
            List<Event.Key> readKeys = altWrite.getSuccessors(Relation.ReadsFrom);
            for (Event.Key readKey : readKeys) {
                try {
                    ExecutionGraphNode readNode = getEventNode(readKey);
                    if (EventUtils.isLockAcquireRead(readNode.getEvent())
                            && Objects.equals(readNode.getEvent().getLocation(), location)
                            && !readNode.happensBefore(write)) {
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
    public List<ExecutionGraphNode> getAlternativeLockWrites(ExecutionGraphNode read) {
        Integer location = read.getEvent().getLocation();
        List<ExecutionGraphNode> allWrites = coherencyOrder.get(location);
        List<ExecutionGraphNode> alternativeWrites = splitNodesBefore(read, allWrites);

        List<ExecutionGraphNode> filteredAlternativeWrites = new ArrayList<>();
        // fold alternativeWrites to exclude lock acquire writes which have a matching lock release
        // write
        Set<Long> taskIDs = new TreeSet<>(Long::compare);
        for (int i = 0; i < alternativeWrites.size(); i++) {
            ExecutionGraphNode alternativeWrite = alternativeWrites.get(i);
            if (EventUtils.isFinalLockWrite(alternativeWrite.getEvent())) {
                // Should not consider any more alternate writes after
                // this since this has already been used to revisit an existing
                // lock acquire read.
                break;
            }
            if (EventUtils.isLockReleaseWrite(alternativeWrite.getEvent())) {
                taskIDs.add(alternativeWrite.getEvent().getTaskId());
                filteredAlternativeWrites.add(alternativeWrite);
            } else if (EventUtils.isLockAcquireWrite(alternativeWrite.getEvent())) {
                Long taskId = alternativeWrite.getEvent().getTaskId();
                if (taskIDs.contains(taskId)) {
                    taskIDs.remove(taskId);
                    continue;
                }
                filteredAlternativeWrites.add(alternativeWrite);
            } else {
                filteredAlternativeWrites.add(alternativeWrite);
            }
        }
        // By now, it contains also the CO max write
        // It might be the lock release write
        // Or it might be a lock acquire write
        // In either case, we remove it since it's not an alternative lock write
        if (filteredAlternativeWrites.isEmpty()) {
            return filteredAlternativeWrites;
        }
        return filteredAlternativeWrites.subList(1, filteredAlternativeWrites.size());
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

        // Drop the recently added write ( We fixed this by updating the CO as the last step of the
        // write handling proc)

        List<ExecutionGraphNode> nonPorfWrites = splitNodesBefore(write, otherWrites);

        if (nonPorfWrites.isEmpty()) {
            // No writes after the given write event
            // Should not happen. There should at least be the init.
            throw HaltExecutionException.error("No writes after the given write event.");
        }

        // Following the sequential consistency model, we only consider non-exclusive writes
        nonPorfWrites.removeIf((w) -> EventUtils.isExclusiveWrite(w.getEvent()));

        List<ExecutionGraphNode> reads = new ArrayList<>();
        for (ExecutionGraphNode alternativeWrite : nonPorfWrites) {
            List<Event.Key> readKeys = alternativeWrite.getSuccessors(Relation.ReadsFrom);
            for (Event.Key readKey : readKeys) {
                try {
                    ExecutionGraphNode readNode = getEventNode(readKey);
                    if (readNode.getEvent().getLocation().equals(write.getEvent().getLocation())) {
                        reads.add(readNode);
                    }
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
     * @param read The read event that the write needs to backward revisit
     * @return The backward revisit view of the ExecutionGraph
     */
    public BackwardRevisitView revisitView(ExecutionGraphNode write, ExecutionGraphNode read) {
        // Construct a restricted view of the graph
        BackwardRevisitView restrictedView = new BackwardRevisitView(this, read, write);
        int readToIndex = getTOIndex(read);
        if (readToIndex == -1) {
            throw HaltCheckerException.error("The read event is not found in the TO order.");
        }

        // The following loop computes the elements of the deleted set.
        for (int i = readToIndex + 1; i < allEvents.size() - 1; i++) {
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
    public List<ExecutionGraphNode> getWrites(Integer location) {
        return coherencyOrder.get(location);
    }

    /**
     * Returns all the writes in the execution graph.
     *
     * @return All the writes in the execution graph.
     */
    public List<ExecutionGraphNode> getAllWrites() {
        List<ExecutionGraphNode> allWrites = new ArrayList<>();
        for (Integer location : coherencyOrder.keySet()) {
            for (ExecutionGraphNode write : coherencyOrder.get(location)) {
                if (write.getEvent().isWrite()) {
                    allWrites.add(write);
                }
            }
        }
        return allWrites;
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
        Integer location = write1.getEvent().getLocation();
        if (!write2.getEvent().getLocation().equals(location)) {
            throw HaltCheckerException.error("The write events are not to the same location.");
        }

        List<ExecutionGraphNode> oldWrites = coherencyOrder.get(location);
        List<ExecutionGraphNode> writes = new ArrayList<>(oldWrites);

        int write1Index = writes.indexOf(write1);
        int write2Index = writes.indexOf(write2);

        if (write1Index == -1 || write2Index == -1) {
            throw HaltCheckerException.error(
                    "One of the write events is not found in the coherency order.");
        }

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
        // TODO :: The following operation is not efficient. It should be optimized.
        for (int i = 0; i < oldWrites.size() - 1; i++) {
            oldWrites.get(i).removeEdge(oldWrites.get(i + 1), Relation.Coherency);
        }
        for (int i = 0; i < writes.size() - 1; i++) {
            writes.get(i).addEdge(writes.get(i + 1), Relation.Coherency);
        }

        coherencyOrder.put(location, writes);
    }

    /**
     * Returns the coherency placings of the given write event under sequential consistency.
     *
     * <p>Writes that are not _porf_-before the given write event. (Tied to Sequential consistency
     * model)
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
            throw HaltCheckerException.error("No writes after the given write event.");
        }
        writesAfter.remove(writesAfter.size() - 1); // removing the only porf-prefix write
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
     * @param read The read event.
     * @param write The write event.
     */
    public void changeReadsFrom(ExecutionGraphNode read, ExecutionGraphNode write) {
        List<Event.Key> writes = read.getPredecessors(Relation.ReadsFrom);
        if (writes.size() != 1) {
            throw HaltCheckerException.error("A read has more than one RF back edge.");
        }
        try {
            ExecutionGraphNode previousWrite = getEventNode(writes.iterator().next());
            previousWrite.removeEdge(read, Relation.ReadsFrom);
            write.addEdge(read, Relation.ReadsFrom);
        } catch (NoSuchEventException e) {
            throw HaltCheckerException.error("The previous write event is not found.");
        }
    }

    /**
     * Sets the reads from relation between the given read and write events.
     *
     * <p>Does not validate if there is an existing reads-from edge to the corresponding read
     *
     * @param read The read event.
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
        Integer location = write.getEvent().getLocation();
        if (!coherencyOrder.containsKey(location)) {
            List<ExecutionGraphNode> writes = new ArrayList<>();
            writes.add(allEvents.get(0));
            coherencyOrder.put(location, writes);
        }
        ExecutionGraphNode previousWrite = allEvents.get(0);
        if (coherencyOrder.get(location).size() > 1) {
            previousWrite =
                    coherencyOrder.get(location).get(coherencyOrder.get(location).size() - 1);
        }
        if (previousWrite.key().equals(write.key())) {
            // No clue why this happens, but it does and need to figure out why!
            return;
        }
        coherencyOrder.get(location).add(write);
        LOGGER.debug(
                "Adding coherency edge between {} and {}",
                previousWrite.getEvent().key().toString(),
                write.getEvent().key().toString());
        previousWrite.addEdge(write, Relation.Coherency);
    }

    public void restrictBySet(Set<Event.Key> set) {
        // We use the following map to track the modified locations of write events.
        // It is used to update the CO-edges.
        Map<Integer, List<ExecutionGraphNode>> modifiedLocations = new HashMap<>();
        for (Event.Key key : set) {
            // Collect and remove the event in the allEvents which it holds the key
            ExecutionGraphNode node = null;
            for (ExecutionGraphNode event : allEvents) {
                if (event.key().equals(key)) {
                    node = event;
                    break;
                }
            }
            if (node == null) {
                throw HaltCheckerException.error("The restricting node is not in all events");
            }

            // Collect the location of the write event
            if (node.getEvent().isWrite() || node.getEvent().isWriteEx()) {
                Integer location = node.getEvent().getLocation();
                if (!modifiedLocations.containsKey(location)) {
                    modifiedLocations.put(location, coherencyOrder.get(location));
                }
            }

            allEvents.removeIf((event) -> event.key().equals(key));

            // Each event is the taskEvents which holds the key must be removed
            int task = Math.toIntExact(key.getTaskId());
            if (task >= taskEvents.size()) {
                throw HaltCheckerException.error("The restricting node is not in task events");
            }
            taskEvents.get(task).removeIf((event) -> event.key().equals(key));

            // Each event in the coherencyOrder which holds the key must be removed
            Integer location = node.getEvent().getLocation();
            if (location != null) {
                if (!coherencyOrder.containsKey(location)) {
                    throw HaltCheckerException.error(
                            "The restricting node is not in coherency order");
                }
                coherencyOrder.get(location).removeIf((e) -> e.key().equals(key));
            }
        }

        // Remove dangling edges
        for (ExecutionGraphNode node : allEvents) {
            Map<Relation, List<Event.Key>> successors = node.getAllSuccessors();
            successors.forEach(
                    (relation, edges) -> {
                        edges.removeIf(set::contains);
                    });
            Map<Relation, List<Event.Key>> predecessors = node.getAllPredecessors();
            predecessors.forEach(
                    (relation, edges) -> {
                        edges.removeIf(set::contains);
                    });
        }

        // Recompute the co-edges
        // TODO :: This approach is not efficient and must be revisited
        for (Map.Entry<Integer, List<ExecutionGraphNode>> entry : modifiedLocations.entrySet()) {
            recomputeCoEdges(entry.getKey(), entry.getValue());
        }
    }

    private void recomputeCoEdges(Integer location, List<ExecutionGraphNode> oldWrites) {
        if (!coherencyOrder.containsKey(location)) {
            throw HaltCheckerException.error("The location is not in the coherency order");
        }

        if (coherencyOrder.get(location).size() == 1) {
            // No need to recompute the edges
            return;
        }

        List<ExecutionGraphNode> writes = coherencyOrder.get(location);
        // Update the edges
        for (int i = 0; i < oldWrites.size() - 1; i++) {
            oldWrites.get(i).removeEdge(oldWrites.get(i + 1), Relation.Coherency);
        }
        for (int i = 0; i < writes.size() - 1; i++) {
            writes.get(i).addEdge(writes.get(i + 1), Relation.Coherency);
        }
    }

    //
    //    public void checkCoBackEdges() {
    //        for (Map.Entry<Integer, List<ExecutionGraphNode>> entry : coherencyOrder.entrySet()) {
    //            for (ExecutionGraphNode write : entry.getValue()) {
    //                List<Event.Key> coBackEdges = write.getPredecessors(Relation.Coherency);
    //                if (coBackEdges != null && coBackEdges.size() > 1) {
    //                    throw HaltExecutionException.error("The previous writes are more than 1");
    //                }
    //            }
    //        }
    //    }

    /** Recomputes the vector clocks of all nodes in the execution graph. */
    public void recomputeVectorClocks() {

        TopologicalSorter topoSorter = new TopologicalSorter(this);
        try {
            topoSorter.sortWithVisitor(
                    new ExecutionGraphNodeVisitor() {

                        private final HashMap<Event.Key, LamportVectorClock> clocks =
                                new HashMap<>();

                        @Override
                        public void visit(ExecutionGraphNode node) {
                            if (node.getEvent().isInit()) {
                                clocks.put(node.key(), new LamportVectorClock(0));
                                return;
                            }

                            if (EventUtils.isBlockingLabel(node.getEvent())) {
                                // Blocking labels are not tracked in the vector clock
                                return;
                            }

                            Event.Key poBeforeNode = node.getPoPredecessor();
                            if (poBeforeNode == null) {
                                // No PO predecessor, this is the first event in the task
                                throw HaltCheckerException.error(
                                        "Invalid PO predecessor for the event.");
                            }
                            LamportVectorClock newClock =
                                    new LamportVectorClock(
                                            clocks.get(poBeforeNode),
                                            Math.toIntExact(node.key().getTaskId()));
                            node.forEachPredecessor(
                                    (relation, preds) -> {
                                        if (relation == Relation.Coherency) {
                                            return;
                                        }
                                        preds.forEach(
                                                (pred) -> {
                                                    LamportVectorClock predClock = clocks.get(pred);
                                                    if (predClock == null) {
                                                        throw HaltCheckerException.error(
                                                                "The predecessors clock is not found.");
                                                    }
                                                    newClock.update(predClock);
                                                });
                                    });

                            // Update the clock of the node
                            clocks.put(node.key(), newClock);
                            node.setVectorClock(newClock);
                        }
                    });
        } catch (TopologicalSorter.GraphCycleException e) {
            throw HaltCheckerException.error("The execution graph is not a DAG.");
        }
    }

    public void restrict(ExecutionGraphNode restrictingNode) {
        // We use the following map to track the modified locations of write events.
        // It is used to update the CO-edges.
        Map<Integer, List<ExecutionGraphNode>> modifiedLocations = new HashMap<>();

        // Removing and storing all inserted events after the restricting node from allEvents (
        // Insertion order )
        int indexRestrictingNode = allEvents.indexOf(restrictingNode);
        if (indexRestrictingNode == -1) {
            throw HaltCheckerException.error("The restricting node is not found.");
        }
        List<ExecutionGraphNode> newAllEvents = new ArrayList<>(indexRestrictingNode + 1);
        List<ExecutionGraphNode> removedNodes =
                new ArrayList<>(allEvents.size() - indexRestrictingNode);

        for (int i = 0; i < allEvents.size(); i++) {
            if (i <= indexRestrictingNode) {
                newAllEvents.add(allEvents.get(i));
            } else {
                removedNodes.add(allEvents.get(i));
            }
        }
        allEvents = newAllEvents;
        // Iterating over these nodes and remove them from the taskEvents and coherencyOrder
        for (ExecutionGraphNode node : removedNodes) {

            if (node.getEvent().isWrite() || node.getEvent().isWriteEx()) {
                // Based on the assumption that the init node is never removed. So, we only have to
                // update the CO if
                // the node is a write or writeEx event.
                Integer location = node.getEvent().getLocation();
                if (!modifiedLocations.containsKey(location)) {
                    modifiedLocations.put(location, coherencyOrder.get(location));
                }
            }

            // Removing from coherencyOrder
            Integer location = node.getEvent().getLocation();

            if (location != null) {
                if (!coherencyOrder.containsKey(location)) {
                    throw HaltCheckerException.error(
                            "The restricting node is not in coherency order");
                }

                coherencyOrder.get(location).removeIf((e) -> e.key().equals(node.key()));
            }

            // Removing from taskEvents
            int task = Math.toIntExact(node.getEvent().getTaskId());
            if (task >= taskEvents.size()) {
                throw HaltCheckerException.error("The restricting node is not in task events");
            }
            taskEvents.get(task).removeIf((e) -> e.key().equals(node.key()));
        }

        // Removing dangling edges
        Set<Event.Key> removedKeys =
                removedNodes.stream().map(ExecutionGraphNode::key).collect(Collectors.toSet());
        for (ExecutionGraphNode node : allEvents) {
            node.forEachSuccessor(
                    (relation, edges) -> {
                        edges.removeIf(removedKeys::contains);
                    });
            node.forEachPredecessor(
                    (relation, edges) -> {
                        edges.removeIf(removedKeys::contains);
                    });
        }

        // Recompute the co-edges
        // TODO :: This approach is not efficient and must be revisited
        for (Map.Entry<Integer, List<ExecutionGraphNode>> entry : modifiedLocations.entrySet()) {
            recomputeCoEdges(entry.getKey(), entry.getValue());
        }
    }

    /** Returns an iterator walking through the nodes in a topological sort order. */
    public List<ExecutionGraphNode> iterator() throws TopologicalSorter.GraphCycleException {
        return (new TopologicalSorter(this)).sort();
    }

    /** Returns List of nodes while silently ignoring any errors with cycles * */
    public List<ExecutionGraphNode> unsafeIterator() {
        try {
            return (new TopologicalSorter(this)).sort();
        } catch (TopologicalSorter.GraphCycleException e) {
            return Collections.emptyList();
        }
    }

    public boolean checkExtensiveConsistency() {
        try {
            checkConsistency();
            //            List<ExecutionGraphNode> topologicalSort =
            // checkConsistencyAndTopologicallySort();

            // Check that finish is the last event in each task
            for (int i = 0; i < taskEvents.size(); i++) {
                List<ExecutionGraphNode> taskEventList = taskEvents.get(i);
                if (taskEventList.isEmpty()) {
                    continue; // No events for this task
                }
                ExecutionGraphNode lastEvent = taskEventList.get(taskEventList.size() - 1);
                if (!EventUtils.isThreadFinish(lastEvent.getEvent())) {
                    LOGGER.error(
                            "Task {} does not end with a finish event: {}",
                            i,
                            lastEvent.getEvent());
                    return false;
                }
            }

            if (!checkCoherencyEdges()) {
                LOGGER.error("Coherency edges are not consistent.");
                return false;
            }
            if (!checkReadsFromEdges()) {
                LOGGER.error("Reads from edges are not consistent.");
                return false;
            }
        } catch (Exception e) {
            // If any exception is thrown, the graph is not consistent
            LOGGER.error("Failed to check consistency of the execution graph: {}", e.getMessage());
            return false;
        }
        return true;
    }

    public boolean checkReadsFromEdges() {
        for (int i = 0; i < allEvents.size(); i++) {
            ExecutionGraphNode node = allEvents.get(i);
            if (!node.getEvent().isRead() && !node.getEvent().isReadEx()) {
                // Only check read events
                continue;
            }
            List<Event.Key> readsFrom = node.getPredecessors(Relation.ReadsFrom);
            if (readsFrom.size() != 1) {
                LOGGER.error(
                        "Read event {} has {} reads from predecessors, expected 1.",
                        node.getEvent().key(),
                        readsFrom.size());
                return false;
            }
        }
        return true;
    }

    public List<ExecutionGraphNode> checkConsistency() {
        ExecutionGraph clone = new ExecutionGraph(this);
        try {
            // Add edges from reads to alternative writes
            for (Map.Entry<Integer, List<ExecutionGraphNode>> writeEntry :
                    clone.coherencyOrder.entrySet()) {
                List<ExecutionGraphNode> writes = writeEntry.getValue();
                for (ExecutionGraphNode write : writes) {
                    Map<Integer, List<ExecutionGraphNode>> readsPerLocation = new HashMap<>();
                    List<Event.Key> reads = write.getSuccessors(Relation.ReadsFrom);

                    for (Event.Key readKey : reads) {
                        try {
                            ExecutionGraphNode readNode = getEventNode(readKey);
                            if (!readNode.getEvent().isReadEx()) {
                                // We only check for read exclusive events
                                continue;
                            }
                            Integer readLocation = readNode.getEvent().getLocation();
                            if (!readsPerLocation.containsKey(readLocation)) {
                                readsPerLocation.put(readLocation, new ArrayList<>());
                            }
                            readsPerLocation.get(readLocation).add(readNode);
                        } catch (NoSuchEventException e) {
                            throw HaltCheckerException.error("The read event is not found.");
                        }
                    }

                    for (Map.Entry<Integer, List<ExecutionGraphNode>> entry :
                            readsPerLocation.entrySet()) {
                        List<ExecutionGraphNode> locationReads = entry.getValue();
                        if (locationReads.size() > 1) {
                            // More than one read to the same location
                            // This is not allowed in the sequential consistency model
                            return new ArrayList<>();
                        }
                    }
                }

                for (int i = 0; i < writes.size() - 1; i++) {
                    ExecutionGraphNode write = writes.get(i);
                    if (write.getEvent().isInit()) {
                        continue;
                    }
                    List<Event.Key> reads = write.getSuccessors(Relation.ReadsFrom);
                    if (reads.isEmpty()) {
                        // No reads from this write, continue
                        continue;
                    }
                    List<ExecutionGraphNode> readNodes = new ArrayList<>();
                    for (Event.Key key : reads) {
                        readNodes.add(clone.getEventNode(key));
                    }
                    ExecutionGraphNode nextWrite = writes.get(i + 1);
                    for (ExecutionGraphNode read : readNodes) {
                        read.addEdge(nextWrite, Relation.FR);
                    }
                }
            }
            return fixTopologicalSort(clone.iterator());
        } catch (NoSuchEventException e) {
            throw HaltCheckerException.error(
                    "Hit an event that doesn't exist in the graph: " + e.getMessage());
        } catch (TopologicalSorter.GraphCycleException e) {
            LOGGER.debug("Hit an inconsistent graph: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<ExecutionGraphNode> checkConsistencyAndTopologicallySort() {
        return checkConsistency();
    }

    private List<ExecutionGraphNode> fixTopologicalSort(List<ExecutionGraphNode> topologicalSort) {
        // The problem arises between ReadEx and WriteEx events of the same task ID.
        // Other events can sneak in between them. since the WriteEx first requires that the ReadEx
        // is scheduled.
        // We need to fix the topological sort by moving the WriteEx event before the ReadEx event.

        List<ExecutionGraphNode> fixedTopologicalSort = new ArrayList<>();

        for (int i = 0; i < topologicalSort.size(); i++) {
            ExecutionGraphNode node = topologicalSort.get(i);
            fixedTopologicalSort.add(node);
            if (EventUtils.isLockAcquireRead(node.getEvent())) {
                ExecutionGraphNode next = topologicalSort.get(i + 1);
                if (EventUtils.isLockAcquireWrite(next.getEvent())
                        && Objects.equals(
                                node.getEvent().getTaskId(), next.getEvent().getTaskId())) {
                    // Next event is a WriteEx event of the same task ID
                    continue;
                }

                // We need to find the WriteEx event of the same task ID
                for (int j = i + 1; j < topologicalSort.size(); j++) {
                    ExecutionGraphNode nextNode = topologicalSort.get(j);
                    if (EventUtils.isLockAcquireWrite(nextNode.getEvent())
                            && Objects.equals(
                                    node.getEvent().getTaskId(), nextNode.getEvent().getTaskId())) {
                        // Move the WriteEx event before the ReadEx event
                        fixedTopologicalSort.add(nextNode);

                        // Remove the WriteEx event from the topological sort
                        topologicalSort.remove(j);
                        break;
                    }
                }
            }
        }
        return fixedTopologicalSort;
    }

    /** Returns true if the graph contains only the initial event. */
    public boolean isEmpty() {
        return allEvents.size() == 1 && allEvents.get(0).getEvent().isInit();
    }

    /** Clears the execution graph. */
    public void clear() {
        allEvents.clear();
        coherencyOrder.clear();
        taskEvents.clear();
        blockedLocks.clear();
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

    public String toJsonStringIgnoreLocation() {
        JsonObject nodes = new JsonObject();
        List<ExecutionGraphNode> sortedEvents = new ArrayList<>(allEvents);
        sortedEvents.sort(
                (o1, o2) -> {
                    return o1.getEvent().key().compareTo(o2.getEvent().key());
                });
        for (ExecutionGraphNode node : sortedEvents) {
            nodes.add(node.key().toString(), node.toJsonIgnoreLocation());
        }
        JsonObject gson = new JsonObject();
        gson.add("nodes", nodes);

        return gson.toString();
    }

    // For debugging
    public void printCO() {
        for (Integer loc : coherencyOrder.keySet()) {
            System.out.println("[Exec Graph debug]: printCO " + loc);
            for (ExecutionGraphNode write : coherencyOrder.get(loc)) {
                System.out.println("[Exec Graph debug]: the writes " + write.getEvent().toString());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecutionGraph that)) {
            return false;
        }

        // Check if the two graphs have the same number of events
        if (this.allEvents.size() != that.allEvents.size()) {
            return false;
        }

        // Check if the two graphs have the same events in topological order
        List<ExecutionGraphNode> curNodes = unsafeIterator();

        List<ExecutionGraphNode> thatNodes = that.unsafeIterator();

        for (int i = 0; i < curNodes.size(); i++) {
            if (!curNodes.get(i).equals(thatNodes.get(i))) {
                return false;
            }
        }

        // Check edges between the nodes
        for (int i = 0; i < curNodes.size(); i++) {
            if (!curNodes.get(i).equalsEdges(thatNodes.get(i))) {
                return false;
            }
        }

        return true;
    }

    public boolean checkCoherencyEdges() {
        for (Map.Entry<Integer, List<ExecutionGraphNode>> entry : coherencyOrder.entrySet()) {
            if (Objects.equals(entry.getKey(), LocationStore.ThreadLocation)) {
                // Skip the thread location
                continue;
            }
            List<ExecutionGraphNode> writes = entry.getValue();
            for (int i = 0; i < writes.size() - 1; i++) {
                ExecutionGraphNode write = writes.get(i);
                if (!write.hasEdge(writes.get(i + 1).getEvent().key(), Relation.Coherency)) {
                    return false;
                }
                if (write.getEvent().isInit()) {
                    // Skip the init event
                    continue;
                }
                List<Event.Key> successiveWrites = write.getSuccessors(Relation.Coherency);
                if (successiveWrites.size() > 1) {
                    // More than one write to the same location
                    // This is not allowed in the sequential consistency model
                    return false;
                }
            }
        }
        return true;
    }

    public void trackThreadJoinCompletion(ExecutionGraphNode eventNode) {
        // TODO: complete this
    }

    // When a new task wants to acquire a lock
    // We keep track of it and add a blocking label
    public void blockTaskForLock(Event event) {
        addBlockingLabel(event.getTaskId());
        if (!blockedLocks.containsKey(event.getLocation())) {
            blockedLocks.put(event.getLocation(), new ArrayList<>());
        }
        blockedLocks.get(event.getLocation()).add(event.getTaskId());
    }

    // When a lock is released,
    // We unblock all the tasks that are waiting for it
    // This is done by removing the blocking label
    // Yet, we retain the task in the blockedLocks map
    public void unblockAllTasksForLock(Integer location) {
        if (!blockedLocks.containsKey(location)) {
            // Nothing to unblock
            return;
        }
        for (Long taskId : blockedLocks.get(location)) {
            unBlockTask(taskId);
        }
    }

    // When a task acquires a lock,
    // We remove it from the blockedLocks map
    // Here the assumption is that the task has already been unblocked
    // Then for all remaining tasks that are waiting for the lock,
    // We add a blocking label
    public void acquireLock(Integer location, Long taskId) {
        if (!blockedLocks.containsKey(location)) {
            return;
        }
        blockedLocks.get(location).remove(taskId);
        if (blockedLocks.get(location).isEmpty()) {
            blockedLocks.remove(location);
            return;
        }
        for (Long taskID : blockedLocks.get(location)) {
            addBlockingLabel(taskID);
        }
    }

    public boolean waitingForLock(Integer location, Long taskId) {
        if (!blockedLocks.containsKey(location)) {
            // No tasks waiting for this.
            // Hence by definition, the current task is not waiting
            return false;
        }
        return blockedLocks.get(location).contains(taskId);
    }

    /** Generic visitor interface for the execution graph nodes. */
    public interface ExecutionGraphNodeVisitor {
        void visit(ExecutionGraphNode node);
    }

    /**
     * Topological sorter for the execution graph.
     *
     * <p>Sorts the nodes in topological order and throws an exception if the graph has cycles.
     */
    public static class TopologicalSorter {

        private final ExecutionGraph graph;
        private final Map<Event.Key, ExecutionGraphNode> nodeMap;

        /**
         * Initializes a new topological sorter for the given graph.
         *
         * @param graph The graph to sort.
         */
        public TopologicalSorter(ExecutionGraph graph) {
            this.graph = graph;
            this.nodeMap = new HashMap<>();
            for (ExecutionGraphNode node : graph.allEvents) {
                nodeMap.put(node.key(), node);
            }

            // Need to include blocking labels in the graph
            for (int i = 0; i < graph.taskEvents.size(); i++) {
                int tasksForI = graph.taskEvents.get(i).size();
                if (tasksForI > 0) {
                    ExecutionGraphNode lastTask = graph.taskEvents.get(i).get(tasksForI - 1);
                    nodeMap.put(lastTask.key(), lastTask);
                }
            }
        }

        /**
         * Sorts the graph in topological order.
         *
         * @return The sorted list of nodes.
         * @throws GraphCycleException If the graph has cycles.
         */
        public List<ExecutionGraphNode> sort() throws GraphCycleException {
            Deque<ExecutionGraphNode> queue = new ArrayDeque<>();
            Map<Event.Key, Integer> inDegreeMap = new HashMap<>();
            List<ExecutionGraphNode> output = new ArrayList<>();

            queue.add(graph.allEvents.get(0));

            for (ExecutionGraphNode node : graph.allEvents) {
                inDegreeMap.put(node.key(), node.getInDegree());
            }

            while (!queue.isEmpty()) {
                ExecutionGraphNode node = queue.pop();
                if (!EventUtils.isBlockingLabel(node.getEvent())) {
                    output.add(node);
                }

                List<Event.Key> toAdd = new ArrayList<>();

                node.forEachSuccessor(
                        (relation, successors) -> {
                            successors.forEach(
                                    successor -> {
                                        int newIndegree =
                                                inDegreeMap.getOrDefault(successor, 1) - 1;
                                        inDegreeMap.put(successor, newIndegree);
                                        if (newIndegree == 0) {
                                            toAdd.add(successor);
                                        }
                                    });
                        });

                toAdd.sort(Event.Key::compareTo);
                toAdd.forEach(
                        (key) -> {
                            if (!nodeMap.containsKey(key)) {
                                LOGGER.debug("Error finding the node for key: {}", key);
                            }
                            queue.add(nodeMap.get(key));
                        });
            }

            if (output.size() != graph.allEvents.size()) {
                throw new GraphCycleException("Graph has cycles");
            } else {
                return output;
            }
        }

        /**
         * Sorts the graph in topological order and visits each node using the given visitor.
         *
         * @param visitor The visitor to use for each node.
         * @throws GraphCycleException If the graph has cycles.
         */
        public void sortWithVisitor(ExecutionGraphNodeVisitor visitor) throws GraphCycleException {
            Deque<ExecutionGraphNode> queue = new ArrayDeque<>();
            Map<Event.Key, Integer> inDegreeMap = new HashMap<>();
            List<Event.Key> output = new ArrayList<>();

            queue.add(graph.allEvents.get(0));

            for (ExecutionGraphNode node : graph.allEvents) {
                inDegreeMap.put(node.key(), node.getInDegree());
            }
            try {
                while (!queue.isEmpty()) {
                    ExecutionGraphNode node = queue.pop();
                    output.add(node.key());
                    visitor.visit(node);

                    List<Event.Key> toAdd = new ArrayList<>();

                    node.forEachSuccessor(
                            (relation, successors) -> {
                                successors.forEach(
                                        successor -> {
                                            int newIndegree =
                                                    inDegreeMap.getOrDefault(successor, 1) - 1;
                                            inDegreeMap.put(successor, newIndegree);
                                            if (newIndegree == 0) {
                                                toAdd.add(successor);
                                            }
                                        });
                            });

                    toAdd.sort(Event.Key::compareTo);
                    toAdd.forEach(
                            (key) -> {
                                if (!nodeMap.containsKey(key)) {
                                    LOGGER.debug("Error finding the node");
                                }
                                queue.add(nodeMap.get(key));
                            });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (output.size() != graph.allEvents.size()) {
                throw new GraphCycleException("Graph has cycles");
            }
        }

        /** Exception thrown when the graph has cycles. */
        public static class GraphCycleException extends Exception {
            /**
             * Initializes a new graph cycle exception with the given message.
             *
             * @param message The message for the exception.
             */
            public GraphCycleException(String message) {
                super(message);
            }
        }
    }

    // For debugging
    public void printGraph() {
        StringBuilder sb = new StringBuilder();
        sb.append("Execution Graph:\n");
        for (int i = 0; i < taskEvents.size(); i++) {
            sb.append("Tasks ").append(i).append(": \n");
            for (ExecutionGraphNode node : taskEvents.get(i)) {
                sb.append(node.getEvent());
                // Print predecessors and successors
                sb.append(" [P: ");
                for (Relation relation : node.getAllPredecessors().keySet()) {
                    sb.append("{").append(relation).append(": ");
                    for (Event.Key key : node.getPredecessors(relation)) {
                        sb.append(key).append("/");
                    }
                    sb.append("} ");
                }
                sb.append("] [S: ");
                for (Relation relation : node.getEdges().keySet()) {
                    sb.append("{").append(relation).append(": ");
                    for (Event.Key key : node.getSuccessors(relation)) {
                        sb.append(key).append("/");
                    }
                    sb.append("] ");
                }
                sb.append(" ---> \n");
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("All Events:\n");
        for (ExecutionGraphNode node : allEvents) {
            sb.append(node.getEvent()).append(" -> ");
        }
        sb.append("\n");
        sb.append("\n");

        sb.append("Coherency Order:\n");
        for (Integer loc : coherencyOrder.keySet()) {
            sb.append("Location ").append(loc).append(": ");
            for (ExecutionGraphNode node : coherencyOrder.get(loc)) {
                sb.append(node.getEvent()).append(" -> ");
            }
            sb.append("\n");
        }
        sb.append("\n");
        LOGGER.debug("{}", sb.toString());
    }

    public boolean isRfConsistent() {
        // For each read event, check if the read-from edge is present
        for (ExecutionGraphNode node : allEvents) {
            if (node.getEvent().isRead()) {
                List<Event.Key> writes = node.getPredecessors(Relation.ReadsFrom);
                if (writes != null && writes.size() != 1) {
                    return false;
                }
            }
        }
        return true;
    }
}
