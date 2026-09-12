package org.mpi_sws.jmc.strategies.trust;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mpi_sws.jmc.util.LamportVectorClock;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * A node in an {@link ExecutionGraph}: one {@link Event} together with its incident edges and its
 * Lamport vector clock.
 *
 * <p>Edges are stored as {@link Event.Key}s (not object references) grouped by {@link Relation},
 * both forwards ({@link #edges}, successors) and backwards ({@link #backEdges}, predecessors), which
 * keeps a node cheap to copy when the graph is cloned. The {@link #vectorClock} tracks the
 * happens-before ("porf") order and is maintained by {@link #addBackEdge}, which merges a
 * predecessor's clock only across causal relations — never across {@link Relation#Coherency} or
 * {@link Relation#FR}.
 */
public class ExecutionGraphNode {

    /** Cached array of all relation kinds, iterated when walking edges. */
    private static final Relation[] allRelations = Relation.values();
    /** The event this node represents. */
    private final Event event;
    /** Extra per-node data (separate from the event's own attributes). */
    private Map<String, Object> attributes;
    /** Outgoing edges (successors), grouped by relation. */
    public final Map<Relation, List<Event.Key>> edges;
    /** Incoming edges (predecessors), grouped by relation. */
    public final Map<Relation, List<Event.Key>> backEdges;

    /** The vector clock of this node; tracks only the {@code (po ∪ rf)+} (porf) relation. */
    private LamportVectorClock vectorClock;

    /**
     * Constructs a node for the given event, deriving its vector clock.
     *
     * <p>The init event gets an empty clock; any other event inherits {@code vectorClock} (its
     * program-order predecessor's clock) and increments its own task's component.
     *
     * @param event The {@link Event} that this node represents.
     * @param vectorClock The program-order predecessor's clock to derive this node's clock from.
     */
    public ExecutionGraphNode(Event event, LamportVectorClock vectorClock) {
        this.event = event;
        this.attributes = new HashMap<>();
        this.edges = new EnumMap<>(Relation.class);
        this.backEdges = new EnumMap<>(Relation.class);
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
        this.event = node.event.clone();
        this.attributes = new HashMap<>(node.attributes);
        this.edges = new EnumMap<>(Relation.class);
        for (Map.Entry<Relation, List<Event.Key>> entry : node.edges.entrySet()) {
            List<Event.Key> newEdges = new ArrayList<>();
            for (Event.Key key : entry.getValue()) {
                newEdges.add(key.clone());
            }
            this.edges.put(entry.getKey(), newEdges);
        }
        this.backEdges = new EnumMap<>(Relation.class);
        for (Map.Entry<Relation, List<Event.Key>> entry : node.backEdges.entrySet()) {
            List<Event.Key> newBackEdges = new ArrayList<>();
            for (Event.Key key : entry.getValue()) {
                newBackEdges.add(key.clone());
            }
            this.backEdges.put(entry.getKey(), newBackEdges);
        }
        this.vectorClock = new LamportVectorClock(node.vectorClock.getVector());
    }

    /**
     * Constructs a new {@link ExecutionGraphNode} copying the given node.
     */
    public ExecutionGraphNode clone() {
        return new ExecutionGraphNode(this);
    }

    /**
     * Returns this node's event key.
     *
     * @return the key of the underlying event.
     */
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
            edges.put(adjacency, new ArrayList<>());
        }
        edges.get(adjacency).add(to.key());
        to.addBackEdge(this, adjacency);
    }

    /**
     * Adds a back edge (predecessor) to this node from {@code from}.
     *
     * <p>This node's vector clock is merged with {@code from}'s clock <em>only</em> when the
     * relation is causal — i.e. neither {@link Relation#Coherency} nor {@link Relation#FR} — which
     * is what keeps the clock equal to the porf order exactly.
     *
     * @param from      The predecessor node.
     * @param adjacency The relation of the edge.
     */
    private void addBackEdge(ExecutionGraphNode from, Relation adjacency) {
        if (adjacency != Relation.Coherency && adjacency != Relation.FR) {
            vectorClock.update(from.getVectorClock());
        }
        if (!backEdges.containsKey(adjacency)) {
            backEdges.put(adjacency, new ArrayList<>());
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

    /**
     * Removes all outgoing edges to the given key, across every relation.
     *
     * <p>There might be dangling references to this node from other nodes that are not handled.
     * Additionally, the vector clock is invalidated unless the edge is CO.
     *
     * @param to The key of the node the edges point to.
     */
    public void removeAllEdgesTo(Event.Key to) {
        for (Relation adjacency : edges.keySet()) {
            edges.get(adjacency).removeIf(key -> key.equals(to));
        }
        // remove adjacency if no more edges
        edges.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Removes the outgoing edge with the given relation to the given key.
     *
     * @param to        The key of the node the edge points to.
     * @param adjacency The relation of the edge.
     */
    public void removeEdgeTo(Event.Key to, Relation adjacency) {
        if (!edges.containsKey(adjacency)) {
            return;
        }
        edges.get(adjacency).removeIf(key -> key.equals(to));
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

    /**
     * Removes the given node as a predecessor across every relation.
     *
     * @param from The predecessor node to remove.
     */
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
    public Map<Relation, List<Event.Key>> getAllSuccessors() {
        return edges;
    }

    /**
     * Returns the neighbours of this node that have the given adjacency.
     *
     * @param adjacency The adjacency of the neighbours.
     * @return The neighbours of this node that have the given adjacency.
     */
    public List<Event.Key> getSuccessors(Relation adjacency) {
        return edges.getOrDefault(adjacency, new ArrayList<>());
    }

    /**
     * Returns the edges of this node.
     *
     * @return The edges of this node.
     */
    public Map<Relation, List<Event.Key>> getEdges() {
        return edges;
    }

    /**
     * Returns whether this node has an edge to the given node with the given adjacency.
     *
     * @param to        The node to which the edge is directed.
     * @param adjacency The adjacency of the edge.
     * @return Whether this node has an edge to the given node with the given adjacency.
     */
    public boolean hasEdge(Event.Key to, Relation adjacency) {
        if (!edges.containsKey(adjacency)) {
            return false;
        }
        return edges.get(adjacency).contains(to);
    }

    /**
     * Returns all the predecessors of this node.
     *
     * @return The predecessors of this node.
     */
    public Map<Relation, List<Event.Key>> getAllPredecessors() {
        return backEdges;
    }

    /**
     * Returns the back edges of this node.
     *
     * @return The back edges of this node.
     */
    public List<Event.Key> getPredecessors(Relation adjacency) {
        return backEdges.get(adjacency);
    }

    /**
     * Returns the number of incoming edges of this node.
     *
     * @return The number of incoming edges of this node.
     */
    public int getInDegree() {
        AtomicInteger inDegree = new AtomicInteger();
        for (Relation relation : allRelations) {
            if (!backEdges.containsKey(relation)) {
                continue;
            }
            backEdges.get(relation).forEach(k -> inDegree.getAndIncrement());
        }
        return inDegree.get();
    }

    /**
     * Applies {@code iterator} to each non-empty group of predecessors, keyed by relation.
     *
     * @param iterator receives each relation and its list of predecessor keys.
     */
    public void forEachPredecessor(BiConsumer<Relation, List<Event.Key>> iterator) {
        for (Relation rel : allRelations) {
            if (!backEdges.containsKey(rel)) {
                continue;
            }
            List<Event.Key> predecessors = backEdges.get(rel);
            if (predecessors.isEmpty()) {
                continue;
            }
            iterator.accept(rel, predecessors);
        }
    }

    /**
     * Applies {@code iterator} to each non-empty group of successors, keyed by relation.
     *
     * @param iterator receives each relation and its list of successor keys.
     */
    public void forEachSuccessor(BiConsumer<Relation, List<Event.Key>> iterator) {
        for (Relation rel : allRelations) {
            if (!edges.containsKey(rel)) {
                continue;
            }
            List<Event.Key> successors = edges.get(rel);
            if (successors.isEmpty()) {
                continue;
            }
            iterator.accept(rel, successors);
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

    /**
     * Serializes this node (event, attributes, outgoing edges) to JSON for debugging.
     *
     * @return the JSON representation.
     */
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.add("event", event.toJson());
        JsonObject attributesObject = new JsonObject();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue().toString());
        }
        json.add("attributes", attributesObject);
        JsonObject edgesObject = new JsonObject();
        for (Map.Entry<Relation, List<Event.Key>> entry : edges.entrySet()) {
            JsonArray edgeArray = new JsonArray();
            for (Event.Key key : entry.getValue()) {
                edgeArray.add(key.toString());
            }
            edgesObject.add(entry.getKey().toString(), edgeArray);
        }
        json.add("edges", edgesObject);
        return json;
    }

    /**
     * Serializes this node without location and with edges sorted deterministically, so that graphs
     * differing only in concrete locations hash identically (used for coverage counting).
     *
     * @return the location-independent JSON representation.
     */
    public JsonElement toJsonIgnoreLocation() {
        JsonObject json = new JsonObject();
        json.add("event", event.toJsonIgnoreLocation());
        // Sort the attributes by key
        /*JsonObject attributesObject = new JsonObject();
        attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> attributesObject.addProperty(entry.getKey(), entry.getValue().toString()));
        json.add("attributes", attributesObject);*/
        JsonObject edgesObject = new JsonObject();
        /*Relation[] relations = Relation.values();*/
        Relation[] relations =
                Arrays.stream(Relation.values())
                        .sorted(Comparator.comparingInt(Relation::ordinal))
                        .toArray(Relation[]::new);
        for (int i = 0; i < relations.length; i++) {
            Relation relation = relations[i];
            if (!edges.containsKey(relation)) {
                continue;
            }
            List<Event.Key> successors = edges.get(relation);
            if (successors.isEmpty()) {
                continue;
            }
            JsonArray edgeArray = new JsonArray();
            successors.sort(Event.Key::compareTo);
            for (Event.Key key : successors) {
                edgeArray.add(key.toString());
            }
            edgesObject.add(relation.toString(), edgeArray);
        }
        json.add("edges", edgesObject);
        return json;
    }

    /**
     * Returns the predecessor of this node in the program order.
     *
     * @return The predecessor of this node in the program order.
     */
    public Event.Key getPoPredecessor() {
        if (!backEdges.containsKey(Relation.ProgramOrder)) {
            return null;
        }
        List<Event.Key> predecessors = backEdges.get(Relation.ProgramOrder);
        if (predecessors.size() != 1) {
            return null;
        }
        return predecessors.get(0);
    }

    /**
     * Updates the vector clock of this node.
     *
     * @param newClock The new vector clock of this node.
     */
    public void setVectorClock(LamportVectorClock newClock) {
        this.vectorClock = newClock;
    }

    /**
     * Two nodes are equal when their underlying events are equal (edges are compared separately by
     * {@link #equalsEdges(ExecutionGraphNode)}).
     *
     * @param obj the object to compare with.
     * @return true if {@code obj} is a node with an equal event.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ExecutionGraphNode that)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        return this.event.equals(that.event);
    }

    /**
     * Returns whether this node has exactly the same outgoing edges as {@code other} (same relations
     * and same target keys). Used by {@code ExecutionGraph.equals} to compare two graphs.
     *
     * @param other the node to compare edges with.
     * @return true if the outgoing edge sets match.
     */
    public boolean equalsEdges(ExecutionGraphNode other) {
        if (this == other) {
            return true;
        }
        for (Map.Entry<Relation, List<Event.Key>> entry : edges.entrySet()) {
            if (entry.getValue().isEmpty()) {
                if (!other.edges.containsKey(entry.getKey())) {
                    continue;
                }
                List<Event.Key> otherEdges = other.edges.get(entry.getKey());
                if (!otherEdges.isEmpty()) {
                    return false;
                }
            }
            if (!other.edges.containsKey(entry.getKey())) {
                return false;
            }
            if (entry.getValue().size() != other.edges.get(entry.getKey()).size()) {
                return false;
            }
            for (Event.Key key : entry.getValue()) {
                if (!other.edges.get(entry.getKey()).contains(key)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks if this node has a predecessor with the given key and relation.
     *
     * @param key      The key of the predecessor.
     * @param relation The relation of the predecessor.
     * @return True if this node has a predecessor with the given key and relation, false otherwise.
     */
    public boolean hasPredecessor(Event.Key key, Relation relation) {
        if (!backEdges.containsKey(relation)) {
            return false;
        }
        return backEdges.get(relation).contains(key);
    }

    /**
     * Returns the string form of the underlying event.
     *
     * @return the event's string representation.
     */
    @Override
    public String toString() {
        return event.toString();
    }
}
