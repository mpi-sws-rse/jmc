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
 * porf prefix (the events porf-before it, {@code porf = (po ∪ rf)⁺}) as a vector clock. Mutable and small; the
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
 *
 * <p><b>Prefixes as vector clocks.</b> A node's prefix is po-downward-closed (each source enters
 * with its own prefix, which holds its po predecessor), so it is stored as a clock: per thread
 * slot ({@link Slots}), the largest po index in the prefix, −1 for none. The clock is computed
 * once, from the stored clocks of the node's sources (po predecessor, present rf, present hbIn),
 * when the node is appended and when its rf changes; it is never recomputed for other nodes. That
 * is exact because a {@link #setRf}/{@link #setBottom} target never has a porf successor (it was
 * just appended, or it is the receive of a backward revisit, whose kept later events are the
 * revisiting send and its prefix, which excludes the receive), and because a backward revisit's
 * restriction ({@link #restrictTo}) removes no porf ancestor of a kept event. With
 * {@code -Djmc.must.checkPrefixes=true} (not by {@code -ea} alone), every update is checked
 * against a from-scratch computation of all prefixes ({@link #checkPrefixes}).
 */
final class Graph {

    /**
     * Whether every prefix update is checked against a from-scratch computation: only with
     * {@code -Djmc.must.checkPrefixes=true} (the differential tests), never by {@code -ea} alone,
     * since the check costs more than the old recomputation. A failed check inside a Must run is
     * thrown on whichever thread made the update; on the scheduler thread JMC does not end the run,
     * so a guarded run should be given a timeout.
     */
    static final boolean CHECK = Boolean.getBoolean("jmc.must.checkPrefixes");

    /** No prefix: the clock of a node with no porf predecessor. */
    private static final int[] NO_CLOCK = new int[0];

    /**
     * The thread slots of a graph lineage: task id → dense clock index, append-only and shared by
     * every copy and restriction of the graph (a clock shorter than the slot count reads −1 for the
     * slots it lacks).
     */
    static final class Slots {
        private final Map<Long, Integer> slotOf = new HashMap<>();
        private final List<Long> tidOf = new ArrayList<>();

        /** The slot of a task, assigned on first use. */
        int slot(long tid) {
            Integer s = slotOf.get(tid);
            if (s == null) {
                s = tidOf.size();
                slotOf.put(tid, s);
                tidOf.add(tid);
            }
            return s;
        }

        /** The slot of a task, or −1 if it has none. */
        int find(long tid) {
            Integer s = slotOf.get(tid);
            return s == null ? -1 : s;
        }

        /** The task of a slot. */
        long tid(int slot) {
            return tidOf.get(slot);
        }

        /** The number of slots. */
        int count() {
            return tidOf.size();
        }
    }

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
        /**
         * The events porf-before this one (excluding itself), as a clock over {@link #slots}: per
         * slot the largest po index in the prefix, −1 for none. Invariant: an array once stored here
         * is never written (copies share it); every update stores a fresh array.
         */
        int[] clock = NO_CLOCK;
        /** The thread slots of the graph lineage this node belongs to. */
        final Slots slots;
        /** The events ordered before this one by a spawn or a join (immutable). */
        final Set<EventKey> hbIn;

        /** Creates a node with no rf yet and an empty prefix; {@code hbIn} is copied. */
        Node(EventKey key, MpLabel label, Predicate<MpMessage> guard, int stamp, Set<EventKey> hbIn,
                Slots slots) {
            this.key = key;
            this.label = label;
            this.guard = guard;
            this.stamp = stamp;
            this.hbIn = Set.copyOf(hbIn);
            this.slots = slots;
        }

        /** A copy with the same stamp, rf and prefix (the clock is immutable and shared). */
        Node copy() {
            Node n = new Node(key, label, guard, stamp, hbIn, slots);
            n.rf = rf;
            n.bottom = bottom;
            n.clock = clock;
            return n;
        }

        /** Whether {@code e} is porf-before this node. */
        boolean inPrefix(EventKey e) {
            int s = slots.find(e.tid());
            return e.idx() >= 0 && s >= 0 && s < clock.length && e.idx() <= clock[s];
        }

        /** The prefix as a set, enumerated from the clock. */
        Set<EventKey> prefix() {
            Set<EventKey> out = new HashSet<>();
            for (int s = 0; s < clock.length; s++) {
                long tid = slots.tid(s);
                for (int i = 0; i <= clock[s]; i++) {
                    out.add(new EventKey(tid, i));
                }
            }
            return out;
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
    /** The thread slots of this graph's lineage, shared with its copies and restrictions. */
    private final Slots slots;

    Graph() {
        this(new Slots());
    }

    private Graph(Slots slots) {
        this.slots = slots;
    }

    /** The stamp the next appended node will get. */
    int nextStamp() {
        return nextStamp;
    }

    /** A deep copy (nodes are copied; clocks are shared immutable arrays). */
    Graph copy() {
        Graph g = new Graph(slots);
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
        if (CHECK) {
            g.checkPrefixes("copy");
        }
        return g;
    }

    /**
     * The graph restricted to {@code keep} (Must's {@code G|_E}). The kept events of each task must
     * form a prefix of the task in program order, which the caller guarantees (as it does when
     * {@code keep} is the complement of the set deleted by a backward revisit). Stamps are kept.
     *
     * <p>Contract (C1): the kept set must be closed under porf predecessors of its events — a
     * backward revisit's restriction is (see the class comment) — because the kept nodes keep
     * their clocks; an arbitrary restriction that removes a porf ancestor of a kept event would
     * leave a stale clock, which {@link #CHECK} reports. In particular a restriction must not
     * remove the send a kept receive reads (that send is a porf ancestor of the receive).
     *
     * @param keep the events to keep
     * @return the restricted graph; the kept nodes keep their prefixes (a backward revisit's
     *     restriction removes no porf ancestor of a kept event, checked under {@link #CHECK})
     * @throws IllegalStateException if the kept events of a task are not a program-order prefix
     */
    Graph restrictTo(Set<EventKey> keep) {
        Graph g = new Graph(slots);
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
        if (CHECK) {
            g.checkPrefixes("restrictTo");
        }
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
        slots.slot(task);
        Node n = new Node(new EventKey(task, l.size()), label, guard, nextStamp++, hbIn, slots);
        l.add(n);
        byStamp.put(n.stamp, n);
        n.clock = clockOf(n);
        if (CHECK) {
            checkPrefixes("append " + n.key);
        }
        return n;
    }

    /**
     * Points a receive at a send and recomputes the receive's prefix (the receive has no porf
     * successor, so no other prefix changes).
     *
     * <p>Contract (C1): {@code recv} has no porf successor (a just-appended receive, or a backward
     * revisit's receive), and {@code send} is in the graph; otherwise a clock would go stale (a
     * successor's, or the receive's once the send is appended), which {@link #CHECK} reports.
     *
     * @throws IllegalStateException if the receive is porf-before the send (a porf cycle)
     */
    void setRf(EventKey recv, EventKey send) {
        Node r = node(recv);
        if (CHECK) {
            checkNoSuccessor(r);
            if (!contains(send)) {
                throw new IllegalStateException("prefix check: rf target " + recv + " reads the absent send " + send);
            }
        }
        // Unreachable under the contract above (the receive has no successor, so the send cannot
        // have it in its prefix); kept as the defensive form of the old DFS's cycle error.
        if (contains(send) && node(send).inPrefix(recv)) {
            throw new IllegalStateException("porf cycle through " + recv);
        }
        if (r.rf != null) {
            readerOf.remove(r.rf);
        }
        r.rf = send;
        r.bottom = false;
        readerOf.put(send, recv);
        r.clock = clockOf(r);
        if (CHECK) {
            checkPrefixes("setRf " + recv + " <- " + send);
        }
    }

    /**
     * Makes a receive read ⊥ and recomputes the receive's prefix (no other prefix changes).
     * Contract (C1): as {@link #setRf}, {@code recv} has no porf successor.
     */
    void setBottom(EventKey recv) {
        Node r = node(recv);
        if (CHECK) {
            checkNoSuccessor(r);
        }
        if (r.rf != null) {
            readerOf.remove(r.rf);
        }
        r.rf = null;
        r.bottom = true;
        r.clock = clockOf(r);
        if (CHECK) {
            checkPrefixes("setBottom " + recv);
        }
    }

    /**
     * A node's clock from the stored clocks of its sources: its po predecessor, its rf source if
     * present in the graph, and its present spawn/join predecessors, each joined with its own
     * clock and its own position.
     */
    private int[] clockOf(Node n) {
        int[] c = new int[slots.count()];
        java.util.Arrays.fill(c, -1);
        if (n.key.idx() > 0) {
            include(c, node(new EventKey(n.key.tid(), n.key.idx() - 1)));
        }
        if (n.rf != null && contains(n.rf)) {
            include(c, node(n.rf));
        }
        for (EventKey h : n.hbIn) {
            if (contains(h)) {
                include(c, node(h));
            }
        }
        return c;
    }

    /** Joins a source event and its prefix into a clock under construction. */
    private void include(int[] c, Node src) {
        int[] sc = src.clock;
        for (int i = 0; i < sc.length; i++) {
            if (sc[i] > c[i]) {
                c[i] = sc[i];
            }
        }
        int s = slots.find(src.key.tid());
        if (src.key.idx() > c[s]) {
            c[s] = (int) src.key.idx();
        }
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
        return node(b).inPrefix(a);
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

    /**
     * Checks every node's stored prefix against a from-scratch computation ({@link #CHECK} only).
     *
     * @param where the update just made, for the message
     * @throws IllegalStateException on the first node whose prefix differs, naming both sets
     */
    void checkPrefixes(String where) {
        Map<EventKey, Set<EventKey>> memo = new HashMap<>();
        for (Node n : byStamp.values()) {
            Set<EventKey> expected = prefix(n, memo, new HashSet<>());
            Set<EventKey> stored = n.prefix();
            if (!stored.equals(expected)) {
                throw new IllegalStateException("prefix check after " + where + ": node " + n.key + " has "
                        + new java.util.TreeSet<>(stored) + ", from scratch " + new java.util.TreeSet<>(expected));
            }
        }
    }

    /** Checks that no node has {@code r} in its prefix ({@link #CHECK} only). */
    private void checkNoSuccessor(Node r) {
        for (Node n : byStamp.values()) {
            if (n != r && n.inPrefix(r.key)) {
                throw new IllegalStateException(
                        "prefix check: rf target " + r.key + " has the porf successor " + n.key);
            }
        }
    }

    /**
     * The porf prefix of {@code n} computed from scratch ({@link #checkPrefixes}): the union, over
     * its po predecessor, its rf source and its spawn/join predecessors, of that event and its own
     * prefix.
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
        ExecutionGraph eg = b.build();
        if (CHECK) {
            for (Node x : byStamp.values()) {
                for (Node y : byStamp.values()) {
                    if (y.inPrefix(x.key) != eg.porfBefore(x.key, y.key)) {
                        throw new IllegalStateException("prefix check: " + x.key + " porf " + y.key + " is "
                                + y.inPrefix(x.key) + " by the clocks, " + eg.porfBefore(x.key, y.key)
                                + " in the ExecutionGraph");
                    }
                }
            }
        }
        return eg;
    }

    @Override
    public String toString() {
        return "Graph" + byStamp.values();
    }
}
