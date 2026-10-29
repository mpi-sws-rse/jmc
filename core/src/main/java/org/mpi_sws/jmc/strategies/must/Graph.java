package org.mpi_sws.jmc.strategies.must;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The Must strategy's working execution graph (an execution graph of the Must paper, together
 * with its insertion order {@code ≤_G}): per task the nodes in program order, each with its label,
 * its receive predicate, its rf (a send, ⊥, or none yet), its insertion <em>stamp</em>, and its
 * porf prefix (the events porf-before it, {@code porf = (po ∪ rf)⁺}). Mutable and small; the
 * strategy copies it for the exploration stack and restricts it for backward revisits.
 * {@link #toExecutionGraph()} gives the immutable {@code mp.graph} form used by
 * {@code MpConsistency}, {@code GraphCanon} and the summary exporter.
 *
 * <p>Stamps are never renumbered: a restriction keeps the surviving nodes' stamps, and the next
 * node appended gets a stamp above every stamp ever handed out in this graph's lineage — the
 * revisit conditions compare stamps, so a replayed event must keep the stamp it had.
 *
 * <p>Spawn and join edges ({@link Node#hbIn}) are part of porf: a node's prefix includes them,
 * and {@link #toExecutionGraph()} emits them as {@code hb} edges.
 */
final class Graph {

    /** One event of the graph, with its label, reads-from choice and porf prefix. */
    static final class Node {
        /** The event's identity: task id and index in the task's program order. */
        final EventKey key;
        /** What the event does (send, receive, nondet, block, ...). */
        final MpLabel label;
        /** For a receive, the program's predicate on the messages it accepts; else {@code null}. */
        final Predicate<MpMessage> guard;
        /** Insertion order in the graph; the revisit conditions compare stamps. */
        final int stamp;
        /** The send read; {@code null} with {@link #bottom} false means "no rf yet". */
        EventKey rf;
        /** Whether this receive reads ⊥ (finds no message). */
        boolean bottom;
        /** The events porf-before this one (excluding itself). */
        Set<EventKey> prefix = Set.of();
        /** The events ordered before this one by a spawn or a join (immutable). */
        final Set<EventKey> hbIn;

        /** Creates a node with no rf yet and an empty prefix; {@code hbIn} is copied. */
        Node(EventKey key, MpLabel label, Predicate<MpMessage> guard, int stamp, Set<EventKey> hbIn) {
            this.key = key;
            this.label = label;
            this.guard = guard;
            this.stamp = stamp;
            this.hbIn = Set.copyOf(hbIn);
        }

        /** A copy with the same stamp, rf and prefix (the prefix set is immutable and shared). */
        Node copy() {
            Node n = new Node(key, label, guard, stamp, hbIn);
            n.rf = rf;
            n.bottom = bottom;
            n.prefix = prefix;
            return n;
        }

        boolean isRecv() {
            return label instanceof MpLabel.Recv;
        }

        boolean isSend() {
            return label instanceof MpLabel.Send;
        }

        boolean isBlockingRecv() {
            return label instanceof MpLabel.Recv r && r.blocking();
        }

        @Override
        public String toString() {
            return key + "@" + stamp + ":" + label + (rf != null ? "<-" + rf : bottom ? "<-⊥" : "");
        }
    }

    /** The nodes of each task in program order, by task id. */
    private final TreeMap<Long, List<Node>> tasks = new TreeMap<>();
    /** All nodes by stamp, i.e. in insertion order. */
    private final TreeMap<Integer, Node> byStamp = new TreeMap<>();
    /** For each send that is read, the receive reading it. */
    private final Map<EventKey, EventKey> readerOf = new HashMap<>();
    /** The stamp the next appended node gets; it never decreases along a graph's lineage. */
    private int nextStamp;

    Graph() {}

    /** A deep copy (nodes are copied; prefixes are shared immutable sets). */
    Graph copy() {
        Graph g = new Graph();
        for (Map.Entry<Long, List<Node>> e : tasks.entrySet()) {
            List<Node> l = new ArrayList<>(e.getValue().size());
            for (Node n : e.getValue()) {
                Node c = n.copy();
                l.add(c);
                g.byStamp.put(c.stamp, c);
            }
            g.tasks.put(e.getKey(), l);
        }
        g.readerOf.putAll(readerOf);
        g.nextStamp = nextStamp;
        return g;
    }

    /**
     * The graph restricted to {@code keep} (Must's {@code G|_E}). The kept events of each task must
     * form a prefix of the task in program order, which the caller guarantees (as it does when
     * {@code keep} is the complement of the set deleted by a backward revisit). Stamps are kept;
     * a receive whose send was removed keeps its rf and must be re-pointed by the caller.
     *
     * @param keep the events to keep
     * @return the restricted graph, with its porf prefixes recomputed
     * @throws IllegalStateException if the kept events of a task are not a program-order prefix
     */
    Graph restrictTo(Set<EventKey> keep) {
        Graph g = new Graph();
        for (Node n : byStamp.values()) {
            if (!keep.contains(n.key)) {
                continue;
            }
            List<Node> l = g.tasks.computeIfAbsent(n.key.tid(), t -> new ArrayList<>());
            if (n.key.idx() != l.size()) {
                throw new IllegalStateException("restriction breaks program order at " + n.key);
            }
            Node c = n.copy();
            l.add(c);
            g.byStamp.put(c.stamp, c);
            if (c.rf != null) {
                g.readerOf.put(c.rf, c.key);
            }
        }
        g.nextStamp = nextStamp;
        g.recomputePrefixes();
        return g;
    }

    /**
     * Appends the next event of a task with a fresh stamp and no spawn/join predecessors.
     *
     * @param task the task id
     * @param label the event's label
     * @param guard the receive predicate, or {@code null} for a non-receive
     * @return the node
     */
    Node append(long task, MpLabel label, Predicate<MpMessage> guard) {
        return append(task, label, guard, Set.of());
    }

    /**
     * Appends the next event of a task with a fresh stamp and its spawn/join predecessors.
     *
     * @param task the task id
     * @param label the event's label
     * @param guard the receive predicate, or {@code null} for a non-receive
     * @param hbIn the events ordered before this one by a spawn or a join
     * @return the node
     */
    Node append(long task, MpLabel label, Predicate<MpMessage> guard, Set<EventKey> hbIn) {
        List<Node> l = tasks.computeIfAbsent(task, t -> new ArrayList<>());
        Node n = new Node(new EventKey(task, l.size()), label, guard, nextStamp++, hbIn);
        l.add(n);
        byStamp.put(n.stamp, n);
        n.prefix = prefixOf(n);
        return n;
    }

    /** Points a receive at a send and recomputes every prefix. */
    void setRf(EventKey recv, EventKey send) {
        Node r = node(recv);
        if (r.rf != null) {
            readerOf.remove(r.rf);
        }
        r.rf = send;
        r.bottom = false;
        readerOf.put(send, recv);
        recomputePrefixes();
    }

    /** Makes a receive read ⊥ and recomputes every prefix. */
    void setBottom(EventKey recv) {
        Node r = node(recv);
        if (r.rf != null) {
            readerOf.remove(r.rf);
        }
        r.rf = null;
        r.bottom = true;
        recomputePrefixes();
    }

    /**
     * The node of an event.
     *
     * @throws IllegalArgumentException if the graph has no such event
     */
    Node node(EventKey key) {
        List<Node> l = tasks.get(key.tid());
        if (l == null || key.idx() < 0 || key.idx() >= l.size()) {
            throw new IllegalArgumentException("no node " + key);
        }
        return l.get((int) key.idx());
    }

    /** Whether the graph has the event. */
    boolean contains(EventKey key) {
        List<Node> l = tasks.get(key.tid());
        return l != null && key.idx() >= 0 && key.idx() < l.size();
    }

    /** The nodes of a task in program order (empty if it has none). */
    List<Node> task(long task) {
        return tasks.getOrDefault(task, List.of());
    }

    /** A read-only view of all tasks' nodes, by task id. */
    SortedMap<Long, List<Node>> tasks() {
        return Collections.unmodifiableSortedMap(tasks);
    }

    /** All nodes in insertion (stamp) order. */
    Collection<Node> nodesByStamp() {
        return byStamp.values();
    }

    /** The receive reading a send, or {@code null}. */
    EventKey readerOf(EventKey send) {
        return readerOf.get(send);
    }

    /** {@code a porf b}. */
    boolean porfBefore(EventKey a, EventKey b) {
        return node(b).prefix.contains(a);
    }

    /**
     * Index of a send among its task's sends (counting sends only), the {@code sendIndex} a
     * delivery names.
     *
     * @throws IllegalArgumentException if {@code send} is not a send of this graph
     */
    long sendIndexOf(EventKey send) {
        long i = 0;
        for (Node n : task(send.tid())) {
            if (n.key.equals(send)) {
                return i;
            }
            if (n.isSend()) {
                i++;
            }
        }
        throw new IllegalArgumentException("not a send of this graph: " + send);
    }

    /** The number of events. */
    int size() {
        return byStamp.size();
    }

    /** Whether some task ended in a {@link MpLabel.Block} event. */
    boolean hasBlock() {
        for (Node n : byStamp.values()) {
            if (n.label instanceof MpLabel.Block) {
                return true;
            }
        }
        return false;
    }

    /** Recomputes every node's porf prefix (after an rf change or a restriction). */
    void recomputePrefixes() {
        Map<EventKey, Set<EventKey>> memo = new HashMap<>();
        for (Node n : byStamp.values()) {
            n.prefix = prefix(n, memo, new HashSet<>());
        }
    }

    /** The porf prefix of a node, computed from the prefixes of its predecessors. */
    private Set<EventKey> prefixOf(Node n) {
        return prefix(n, new HashMap<>(), new HashSet<>());
    }

    /**
     * The porf prefix of {@code n}: the union, over its po predecessor, its rf source and its
     * spawn/join predecessors, of that event and its own prefix.
     *
     * @param memo prefixes already computed in this pass
     * @param onPath the nodes being computed, to detect a porf cycle
     * @throws IllegalStateException if porf has a cycle through {@code n}
     */
    private Set<EventKey> prefix(Node n, Map<EventKey, Set<EventKey>> memo, Set<EventKey> onPath) {
        Set<EventKey> done = memo.get(n.key);
        if (done != null) {
            return done;
        }
        if (!onPath.add(n.key)) {
            throw new IllegalStateException("porf cycle through " + n.key);
        }
        Set<EventKey> out = new HashSet<>();
        if (n.key.idx() > 0) {
            Node pred = node(new EventKey(n.key.tid(), n.key.idx() - 1));
            out.add(pred.key);
            out.addAll(prefix(pred, memo, onPath));
        }
        if (n.rf != null && contains(n.rf)) {
            Node src = node(n.rf);
            out.add(src.key);
            out.addAll(prefix(src, memo, onPath));
        }
        for (EventKey h : n.hbIn) {
            if (contains(h)) {
                Node src = node(h);
                out.add(src.key);
                out.addAll(prefix(src, memo, onPath));
            }
        }
        onPath.remove(n.key);
        Set<EventKey> frozen = Set.copyOf(out);
        memo.put(n.key, frozen);
        return frozen;
    }

    /**
     * The immutable {@code mp.graph} form. A receive that has neither an rf nor ⊥ is left without
     * a reads-from edge, which the consistency check reports.
     */
    ExecutionGraph toExecutionGraph() {
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        for (Node n : byStamp.values()) {
            if (n.isRecv()) {
                b.add(GraphEvent.receive(n.key, (MpLabel.Recv) n.label, n.guard));
            } else {
                b.add(GraphEvent.of(n.key, n.label));
            }
        }
        for (Node n : byStamp.values()) {
            if (n.isRecv()) {
                if (n.rf != null && contains(n.rf)) {
                    b.readsFrom(n.key, n.rf);
                } else if (n.bottom) {
                    b.readsBottom(n.key);
                }
            }
            for (EventKey h : n.hbIn) {
                if (contains(h)) {
                    b.hb(h, n.key);
                }
            }
        }
        return b.build();
    }

    @Override
    public String toString() {
        return "Graph" + byStamp.values();
    }
}
