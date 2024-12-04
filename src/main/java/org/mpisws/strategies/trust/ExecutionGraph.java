package org.mpisws.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.util.aux.LamportVectorClock;

import java.util.*;

/**
 * Represents an execution graph.
 *
 * <p>Contains the exploration and all the relations defined according to the Trust algorithm
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
            this.allEvents.add(this.taskEvents.get(nodeKey.getTaskId().intValue()).get(nodeKey.getTimestamp()));
        }
        this.coherencyOrder = new HashMap<>();
        for (Location location : graph.coherencyOrder.keySet()) {
            List<ExecutionGraphNode> writes = graph.coherencyOrder.get(location);
            List<ExecutionGraphNode> newWrites = new ArrayList<>();
            for (ExecutionGraphNode write : writes) {
                Event.Key nodeKey = write.key();
                newWrites.add(this.taskEvents.get(nodeKey.getTaskId().intValue()).get(nodeKey.getTimestamp()));
            }
            this.coherencyOrder.put(location, newWrites);
        }

    }

    public List<Long> getTaskSchedule() {
        return allEvents.stream().map(e -> e.getEvent().getTaskId()).toList();
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

    /**
     * Adds an event to the execution graph.
     *
     * @param event The event to add.
     */
    public void addEvent(Event event) {

        if (event.isInit()) {
            // Add the initial event to the TO order
            allEvents.add(new ExecutionGraphNode(event, new LamportVectorClock(1)));
            return;
        }

        // Track the event in the PO order (fetch the latest vector clock first and use that to create a node)
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
        allEvents.add(node);

        // Track the event in the CO order
        if (event.getType() == Event.Type.WRITE) {
            Location location = event.getLocation();
            if (!coherencyOrder.containsKey(location)) {
                coherencyOrder.put(location, new ArrayList<>());
            }
            coherencyOrder.get(location).add(node);
        }

        // Update the reads from relation
        if (event.getType() == Event.Type.READ) {
            ExecutionGraphNode latestWrite = allEvents.get(0);
            if (coherencyOrder.containsKey(event.getLocation())) {
                List<ExecutionGraphNode> writes = coherencyOrder.get(event.getLocation());
                latestWrite = writes.get(writes.size() - 1);
            }
            latestWrite.addEdge(node, Relation.ReadsFrom);
        }
    }

    protected Event getCOMax(Location location) {
        List<ExecutionGraphNode> writes = coherencyOrder.get(location);
        if (writes == null || writes.isEmpty()) {
            return null;
        }
        return writes.get(writes.size() - 1).getEvent();
    }

    /**
     * Updates the reads from relation between the given read and write events.
     *
     * <p>Invalidates the total order and the vector clocks of events in the graph.
     * The concern of fixing the total order and the vector clocks is passed to the calling function.
     *
     * @param read  The read event.
     * @param write The write event.
     */
    public void setReadsFrom(ExecutionGraphNode read, ExecutionGraphNode write) {
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
     * Restricts the execution graph to the given node. Meaning, all events that are not in the
     * causal prefix of the given node are removed.
     *
     * <p>Recompute the vector clocks of all nodes and delete those nodes that are not
     * happens-before the given node.
     *
     * @param restrictingNode The node to restrict to.
     */
    public void restrictTo(ExecutionGraphNode restrictingNode) {
        // First recompute vector clocks
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

        List<ExecutionGraphNode> newAllEvents = new ArrayList<>();
        for (ExecutionGraphNode iterNode : allEvents) {
            if (iterNode.happensBefore(restrictingNode)) {
                newAllEvents.add(iterNode);
            }
            if (restrictingNode.happensBefore(iterNode)) {
                // We have gone past in the TO. By definition, there are no other nodes that we need to include.
                break;
            }
        }
        allEvents = newAllEvents;

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

    /**
     * Returns true if the graph contains only the initial event.
     */
    public boolean isEmpty() {
        return allEvents.size() == 1 && allEvents.get(0).getEvent().isInit();
    }

    /**
     * Returns an iterator that iterates over the nodes in the graph in topological order.
     *
     * <p>Note: can be improved by using DFS to do the ordering</p>
     *
     * <p>Does not validate if the graph has cycles!</p>
     */
    public static class TopologicalIterator implements Iterator<ExecutionGraphNode> {
        private final Queue<ExecutionGraphNode> queue;
        private final Set<Event.Key> visited;
        private final ExecutionGraph graph;

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
