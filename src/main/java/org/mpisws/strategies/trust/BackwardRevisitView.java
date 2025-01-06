package org.mpisws.strategies.trust;

import org.mpisws.runtime.HaltExecutionException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

// Represents a restricted view of the execution graph.
// Some nodes are removed and some relations are updated.
public class BackwardRevisitView {
    private final ExecutionGraph graph;
    private HashSet<Event.Key> removedNodes;
    private final ExecutionGraphNode read;
    private final ExecutionGraphNode write;

    public BackwardRevisitView(
            ExecutionGraph graph, ExecutionGraphNode read, ExecutionGraphNode write) {
        this.graph = graph.clone();
        this.removedNodes = new HashSet<>();
        this.read = read;
        this.write = write;
    }

    public ExecutionGraphNode getNode(Event.Key key) throws NoSuchEventException {
        if (removedNodes.contains(key)) {
            throw new NoSuchEventException(key);
        }
        return graph.getEventNode(key);
    }

    public boolean containedInView(Event.Key key) {
        return !removedNodes.contains(key) && graph.contains(key);
    }

    public ExecutionGraph getGraph() {
        return graph;
    }

    // Just marks the node as removed, does not update the graph
    public void removeNode(Event.Key key) {
        removedNodes.add(key);
    }

    // Checks if the restricted view is a maximal extension
    // Meta: Breaks the separation of concerns. Is part of the core logic of the Trust algorithm
    public boolean isMaximalExtension(ExecutionGraphNode write, ExecutionGraphNode read) {
        HashSet<Event.Key> nodesToCheck = new HashSet<>(this.removedNodes);
        nodesToCheck.add(read.key());
        try {
            for (Event.Key key : nodesToCheck) {
                ExecutionGraphNode node = graph.getEventNode(key);
                int nodeTOIndex = graph.getTOIndex(node);
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

    // TODO: continue here
    public ExecutionGraph getRestrictedGraph() {
        ExecutionGraph restrictedGraph = graph.clone();
        for (Event.Key key : removedNodes) {
            restrictedGraph.removeEvent(key);
        }
        return restrictedGraph;
    }
}
