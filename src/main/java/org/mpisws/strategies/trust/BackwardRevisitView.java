package org.mpisws.strategies.trust;

import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.runtime.HaltExecutionException;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Represents a restricted view of the execution graph. Some nodes are removed and some relations
 * are updated.
 */
public class BackwardRevisitView {
    private final ExecutionGraph graph;
    private final HashSet<Event.Key> removedNodes;
    private final ExecutionGraphNode read;
    private final ExecutionGraphNode write;

    /**
     * Creates a new backward revisit view.
     *
     * @param graph The execution graph.
     * @param read The read event.
     * @param write The write event.
     */
    public BackwardRevisitView(
            ExecutionGraph graph, ExecutionGraphNode read, ExecutionGraphNode write) {
        this.graph = graph.clone();
        this.removedNodes = new HashSet<>();
        try {
            this.read = this.graph.getEventNode(read.key());
            this.write = this.graph.getEventNode(write.key());
        } catch (NoSuchEventException ignored) {
            throw new HaltCheckerException("The read or write event is not found.");
        }
    }

    public ExecutionGraphNode getWrite() {
        return write;
    }

    public ExecutionGraphNode getRead() {
        return read;
    }

    /** Just marks the node as removed, does not update the graph */
    public void removeNode(Event.Key key) {
        removedNodes.add(key);
    }

    /**
     * Checks if the restricted view is a maximal extension
     *
     * <p>Meta: Breaks the separation of concerns. Is part of the core logic of the Trust algorithm
     */
    public boolean isMaximalExtension() {
        HashSet<Event.Key> nodesToCheck = new HashSet<>(this.removedNodes);
        nodesToCheck.add(read.key());
        try {
            for (Event.Key key : nodesToCheck) {
                ExecutionGraphNode node = graph.getEventNode(key);
                Integer nodeTOIndex = node.getEvent().getToStamp();
                if (nodeTOIndex == null) {
                    throw HaltExecutionException.error("The event does not have a TO index.");
                }
                Predicate<Event.Key> previous =
                        (k) -> {
                            try {
                                ExecutionGraphNode kNode = graph.getEventNode(k);
                                return graph.getTOIndex(k) <= nodeTOIndex
                                        || kNode.happensBefore(node);
                            } catch (NoSuchEventException e) {
                                return false;
                            }
                        };
                // 1. Check first if key is a write event that has a dangling read in the
                // restricted graph.
                if (EventUtils.isWrite(node.getEvent())) {
                    Set<Event.Key> reads = node.getSuccessors(Relation.ReadsFrom);
                    for (Event.Key readKey : reads) {
                        // Check if in previous
                        if (previous.test(readKey)) {
                            continue;
                        }
                        if (!removedNodes.contains(readKey) && graph.contains(readKey)) {
                            return false;
                        }
                    }
                }
                // 2. Check if the write event associated with the node is CO maximal in previous
                ExecutionGraphNode nodeWrite = node;
                if (EventUtils.isRead(node.getEvent())) {
                    Set<Event.Key> writes = node.getPredecessors(Relation.ReadsFrom);
                    if (writes.size() != 1) {
                        throw HaltExecutionException.error(
                                "The read event does not have a valid rf event.");
                    }
                    nodeWrite = graph.getEventNode(writes.iterator().next());
                }
                if (!previous.test(nodeWrite.key())) {
                    return false;
                }
                // Now node is a write event for sure
                // We only need to check if the CO after events for the same location are in
                // previous
                boolean check = false;
                for (ExecutionGraphNode locationWrite :
                        graph.getWrites(nodeWrite.getEvent().getLocation())) {
                    if (locationWrite.equals(nodeWrite)) {
                        check = true;
                        continue;
                    }
                    if (!check) {
                        continue;
                    }
                    if (previous.test(locationWrite.key())) {
                        return false;
                    }
                }
            }
        } catch (NoSuchEventException e) {
            throw HaltExecutionException.error("The event is not found.");
        }
        return true;
    }

    /**
     * Gets the restricted graph.
     *
     * @return The restricted graph
     */
    public ExecutionGraph getRestrictedGraph() {
        ExecutionGraph restrictedGraph = graph.clone();
        // Remove the nodes
        restrictedGraph.restrictByRemoving(removedNodes);
        // Update the reads-from relation
        restrictedGraph.changeReadsFrom(read, write);
        return restrictedGraph;
    }
}
