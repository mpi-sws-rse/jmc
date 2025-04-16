package org.mpisws.jmc.strategies.trust;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mpisws.jmc.runtime.HaltCheckerException;
import org.mpisws.jmc.util.aux.LamportVectorClock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a node in the execution graph.
 */
public class ExecutionGraphNode {
    // The event that this node represents.
    private final Event event;
    // The attributes of this node.
    private Map<String, Object> attributes;
    // Forward edges from this node. Grouped by edge relation.
    private final Map<Relation, Set<Event.Key>> edges;
    // Back edges to this node. Grouped by edge relation.
    private final Map<Relation, Set<Event.Key>> backEdges;

    // The vector clock of this node (Used to track only PORF relation)
    private LamportVectorClock vectorClock;

    /**
     * Constructs a new {@link ExecutionGraphNode} with the given event.
     *
     * @param event The {@link Event} that this node represents.
     */
    public ExecutionGraphNode(Event event, LamportVectorClock vectorClock) {
        this.event = event;
        this.attributes = new HashMap<>();
        this.edges = new HashMap<>();
        this.backEdges = new HashMap<>();
        this.vectorClock =
                event.isInit()
                        ? new LamportVectorClock(0)
                        : new LamportVectorClock(vectorClock, event.getTaskId().intValue());
    }

    /**
     * Copy constructor.
     *
     * @param node The node to copy.
     */
    private ExecutionGraphNode(ExecutionGraphNode node) {
        this.event = node.event;
        this.attributes = new HashMap<>(node.attributes);
        this.edges = new HashMap<>();
        for (Map.Entry<Relation, Set<Event.Key>> entry : node.edges.entrySet()) {
            this.edges.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        this.backEdges = new HashMap<>();
        for (Map.Entry<Relation, Set<Event.Key>> entry : node.backEdges.entrySet()) {
            this.backEdges.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        this.vectorClock = new LamportVectorClock(node.vectorClock.getVector());
    }

    /**
     * Constructs a new {@link ExecutionGraphNode} copying the given node.
     */
    public ExecutionGraphNode clone() {
        return new ExecutionGraphNode(this);
    }

    public Event.Key key() {
        return event.key();
    }

    /**
     * Returns the vector clock of this node.
     *
     * @return The vector clock of this node.
     */
    public LamportVectorClock getVectorClock() {
        return vectorClock;
    }

    /**
     * Adds an edge to this node. The edge is directed from this node to the given node with the
     * given adjacency.
     *
     * @param to        The node to which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    public void addEdge(ExecutionGraphNode to, Relation adjacency) {
        if (!edges.containsKey(adjacency)) {
            edges.put(adjacency, new HashSet<>());
        }
        edges.get(adjacency).add(to.key());
        to.addBackEdge(this, adjacency);
    }

    /**
     * Adds a back edge to this node. The edge is directed from the given node to this node with the
     * given adjacency. The vector clock of this node is updated with the vector clock of the given
     * node (only if the relation is not CO).
     *
     * @param from      The node from which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    private void addBackEdge(ExecutionGraphNode from, Relation adjacency) {
        if (adjacency != Relation.Coherency) {
            vectorClock.update(from.getVectorClock());
        }
        if (!backEdges.containsKey(adjacency)) {
            backEdges.put(adjacency, new HashSet<>());
        }
        backEdges.get(adjacency).add(from.key());
    }

    /**
     * Removes the edge with the given adjacency from this node.
     *
     * <p>Note that removing an edge invalidates the vector clock of all descendants. The concern of
     * fixing the vector clocks is passed to the calling function.
     *
     * @param to        The node to which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    public void removeEdge(ExecutionGraphNode to, Relation adjacency) {
        if (!edges.containsKey(adjacency)) {
            return;
        }
        edges.get(adjacency).removeIf(key -> key.equals(to.key()));
        to.removeBackEdge(this, adjacency);
    }

    /**
     * Removes the edge with the given relation from this node.
     *
     * <p>Leads to dandling references
     *
     * @param relation The relation of the edge.
     */
    public void removeEdge(Relation relation) {
        edges.remove(relation);
        backEdges.remove(relation);
    }

    /*
     * Removes all edges to the given node.
     *
     * <p> There might be dangling references to this node from other nodes that are not handled.
     * Additionally, the vector clock is invalidated unless the edge is CO.
     *
     * @param to The node to which the edges are directed.
     */
    public void removeAllEdgesTo(Event.Key to) {
        for (Relation adjacency : edges.keySet()) {
            edges.get(adjacency).removeIf(key -> key.equals(to));
        }
        // remove adjacency if no more edges
        edges.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Removes all edges from the given node.
     *
     * @param from The node from which the edges are directed.
     */
    public void removeAllEdgesFrom(Event.Key from) {
        for (Relation adjacency : backEdges.keySet()) {
            backEdges.get(adjacency).removeIf(key -> key.equals(from));
        }
        // remove adjacency if no more edges
        backEdges.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Removes the predecessor with the given adjacency from this node.
     *
     * @param from      The node from which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    public void removePredecessor(ExecutionGraphNode from, Relation adjacency) {
        // Wraps around removeBackEdge because the external user does not know about back-edges.
        // Only successors and predecessors.
        removeBackEdge(from, adjacency);
    }

    public void removePredecessor(ExecutionGraphNode from) {
        for (Relation adjacency : backEdges.keySet()) {
            backEdges.get(adjacency).removeIf(key -> key.equals(from.key()));
        }
    }

    /**
     * Removes all the predecessors with the given adjacency from this node.
     *
     * @param adjacency The adjacency of the edges.
     */
    public void removeAllPredecessors(Relation adjacency) {
        if (!backEdges.containsKey(adjacency)) {
            return;
        }
        backEdges.get(adjacency).clear();
    }

    /**
     * Removes the back edge with the given adjacency from this node.
     *
     * @param from      The node from which the edge is directed.
     * @param adjacency The adjacency of the edge.
     */
    private void removeBackEdge(ExecutionGraphNode from, Relation adjacency) {
        if (!backEdges.containsKey(adjacency)) {
            return;
        }
        backEdges.get(adjacency).removeIf(key -> key.equals(from.key()));
    }

    /**
     * Returns the edges of this node.
     *
     * @return The edges of this node.
     */
    public Set<Event.Key> getAllSuccessors() {
        Set<Event.Key> neighbors = new HashSet<>();
        for (Set<Event.Key> edge : edges.values()) {
            neighbors.addAll(edge);
        }
        return neighbors;
    }

    /**
     * Returns the neighbours of this node that have the given adjacency.
     *
     * @param adjacency The adjacency of the neighbours.
     * @return The neighbours of this node that have the given adjacency.
     */
    public Set<Event.Key> getSuccessors(Relation adjacency) {
        if (!edges.containsKey(adjacency)) {
            return new HashSet<>();
        }
        return new HashSet<>(edges.get(adjacency));
    }

    public Map<Relation, Set<Event.Key>> getEdges() {
        return edges;
    }

    /**
     * Returns all the predecessors of this node.
     *
     * @return The predecessors of this node.
     */
    public Set<Event.Key> getAllPredecessors() {
        Set<Event.Key> neighbors = new HashSet<>();
        for (Set<Event.Key> edge : backEdges.values()) {
            neighbors.addAll(edge);
        }
        return neighbors;
    }

    public Map<Relation, Set<Event.Key>> getBackEdges() {
        return backEdges;
    }

    /**
     * Returns the back edges of this node.
     *
     * @return The back edges of this node.
     */
    public Set<Event.Key> getPredecessors(Relation adjacency) {
        if (!backEdges.containsKey(adjacency)) {
            return new HashSet<>();
        }
        return new HashSet<>(backEdges.get(adjacency));
    }

    /**
     * Recomputes the vector clock based on the vector clocks of all _porf_-predecessors. The new
     * vector clock is created based on the PO previous node.
     *
     * @param poBeforeNode The PO previous node to initialize the new vector clock
     * @param nodeProvider A function to get Node for a given key.
     */
    public void recomputeVectorClock(ExecutionGraphNode poBeforeNode, NodeProvider nodeProvider) {
        LamportVectorClock newVectorClock =
                new LamportVectorClock(poBeforeNode.getVectorClock(), event.getTaskId().intValue());
        try {
            Set<Event.Key> porfPredecessors = getPredecessors(Relation.ProgramOrder);
            porfPredecessors.addAll(getPredecessors(Relation.ReadsFrom));
            porfPredecessors.addAll(getPredecessors(Relation.ThreadJoin));
            porfPredecessors.addAll(getPredecessors(Relation.ThreadCreation));
            porfPredecessors.addAll(getPredecessors(Relation.ThreadStart));

            for (Event.Key key : porfPredecessors) {
                if (key.equals(poBeforeNode.key())) {
                    continue;
                }
                ExecutionGraphNode node = nodeProvider.get(key);
                newVectorClock.update(node.getVectorClock());
            }
            vectorClock = newVectorClock;
        } catch (NoSuchEventException e) {
            throw new HaltCheckerException(e.getMessage());
        }
    }

    /**
     * Check if `this` node is happens-before (_porf_ relation) the `other` node.
     *
     * <p>Determined using vector clocks
     *
     * @param other The other node to compare against.
     * @return Returns true if the `this` is happens-before `other`
     */
    public boolean happensBefore(ExecutionGraphNode other) {
        return vectorClock.happensBefore(other.getVectorClock());
    }

    /**
     * Updates the attributes of this node.
     *
     * @param attributes The new attributes of this node.
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Adds an attribute to this node.
     *
     * @param key   The key of the attribute.
     * @param value The value of the attribute.
     */
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Returns the attribute with the given key.
     *
     * @param key The key of the attribute.
     * @param <T> The type of the attribute.
     * @return The attribute with the given key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Returns the {@link Event} that this node represents.
     *
     * @return The event that this node represents.
     */
    public Event getEvent() {
        return event;
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.add("event", event.toJson());
        JsonObject attributesObject = new JsonObject();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue().toString());
        }
        json.add("attributes", attributesObject);
        JsonObject edgesObject = new JsonObject();
        for (Map.Entry<Relation, Set<Event.Key>> entry : edges.entrySet()) {
            JsonArray edgeArray = new JsonArray();
            for (Event.Key key : entry.getValue()) {
                edgeArray.add(key.toString());
            }
            edgesObject.add(entry.getKey().toString(), edgeArray);
        }
        json.add("edges", edgesObject);
        return json;
    }

    @FunctionalInterface
    public interface NodeProvider {
        ExecutionGraphNode get(Event.Key key) throws NoSuchEventException;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) return false;
        ExecutionGraphNode that = (ExecutionGraphNode) obj;
        return that.key().equals(this.key());
    }
}
