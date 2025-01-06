package org.mpisws.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.util.aux.LamportVectorClock;

import java.util.*;
import java.util.function.Predicate;

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

    /** Initializes a new execution graph. */
    public ExecutionGraph() {
        this.allEvents = new ArrayList<>();
        this.coherencyOrder = new HashMap<>();
        this.taskEvents = new ArrayList<>();
    }

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
     * Returns the list of task identifiers in the execution graph using the TO order.
     *
     * @return The list of task identifiers in the execution graph.
     */
    public List<Long> getTaskSchedule() {
        return allEvents.stream().map(e -> e.getEvent().getTaskId()).toList();
    }

    /**
     * Returns the index of the given node in the TO order.
     *
     * @param node The node to get the index of.
     * @return The index of the given node in the TO order (-1 if not found).
     */
    protected int getTOIndex(ExecutionGraphNode node) {
        for (int i = 0; i < allEvents.size(); i++) {
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

    public ExecutionGraphNode getEventNode(Event.Key key) throws NoSuchEventException {
        if (key.getTaskId() == null || key.getTimestamp() == null) {
            // Init event
            return allEvents.get(0);
        }
        int taskID = key.getTaskId().intValue();
        Integer timestamp = key.getTimestamp();
        if (taskID >= taskEvents.size() || timestamp >= taskEvents.get(taskID).size()) {
            throw new NoSuchEventException(key);
        }
        return taskEvents.get(taskID).get(timestamp);
    }

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
            return initialNode;
        }

        // Track the event in the PO order (fetch the latest vector clock first and use that to
        // create a node)
        int task = Math.toIntExact(event.getTaskId());
        if (task >= taskEvents.size()) {
            taskEvents.add(new ArrayList<>());
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

        // Track the event in the TO order
        allEvents.add(node);

        return node;
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
     * model)
     *
     * @param read The read event node.
     * @return The alternative writes to the given read event.
     */
    public List<ExecutionGraphNode> getAlternativeWrites(ExecutionGraphNode read) {
        List<ExecutionGraphNode> allWrites = coherencyOrder.get(read.getEvent().getLocation());
        return splitNodesBefore(read, allWrites);
    }

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

    protected BackwardRevisitView revisitView(ExecutionGraphNode write, ExecutionGraphNode read) {
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
     * Resets the coherency order between the given write events.
     *
     * <p>Invalidates the total order of events in the graph. The concern of fixing the total order
     * is passed to the calling function.
     *
     * @param write1 The first write event.
     * @param write2 The second write event.
     */
    public void resetCoherence(ExecutionGraphNode write1, ExecutionGraphNode write2) {
        // Update the coherency order
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
     * @param read The read event.
     * @param write The write event.
     */
    public void resetReadsFrom(ExecutionGraphNode read, ExecutionGraphNode write) {
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
        previousWrite.addEdge(write, Relation.Coherency);
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
        for (Iterator<ExecutionGraphNode> it = iterator(); it.hasNext(); ) {
            ExecutionGraphNode iterNode = it.next();
            if (iterNode.getAllPredecessors().isEmpty()) {
                // This is the init node, safely continue
                continue;
            }
            Set<Event.Key> poPredecessors = iterNode.getPredecessors(Relation.ProgramOrder);
            if (poPredecessors.size() != 1) {
                throw new HaltCheckerException("A node has more than one PO predecessor");
            }
            try {
                ExecutionGraphNode lastPONode = getEventNode(poPredecessors.iterator().next());
                iterNode.recomputeVectorClock(lastPONode, this::getEventNode);
            } catch (NoSuchEventException e) {
                throw new HaltCheckerException(e.getMessage());
            }
        }

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
        for (Location location : coherencyOrder.keySet()) {
            if (!locationsToKeep.contains(location)) {
                coherencyOrder.remove(location);
                continue;
            }
            List<ExecutionGraphNode> writes = coherencyOrder.get(location);
            List<ExecutionGraphNode> newWrites = new ArrayList<>();
            for (ExecutionGraphNode write : writes) {
                if (write.happensBefore(restrictingNode)) {
                    newWrites.add(write);
                }
            }
            coherencyOrder.put(location, newWrites);
        }

        // Handle dangling reads
        // TODO: complete this
    }

    // Returns an iterator walking through the nodes in a topological sort order.
    public Iterator<ExecutionGraphNode> iterator() {
        return new TopologicalIterator(this);
    }

    public boolean isConsistent() {
        // TODO: Implement this method
        return true;
    }

    /** Returns true if the graph contains only the initial event. */
    public boolean isEmpty() {
        return allEvents.size() == 1 && allEvents.get(0).getEvent().isInit();
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
                for (Event.Key child : node.getAllSuccessors()) {
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
