package org.mpi_sws.jmc.mp.graph;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * An execution graph {@code G = ⟨E, po, rf⟩} (Definition 3.2 of the Must paper) plus the
 * insertion order {@code ≤_G} (Section 4 of the paper) and JMC's extra causal edges.
 *
 * <ul>
 *   <li>{@code E} — the events, grouped per thread; {@code po} is the index order within a
 *       thread ({@link EventKey#idx()}), so it is never stored;</li>
 *   <li>{@code rf} — for each receive, the send it reads, or {@code ⊥} (recorded separately; only
 *       a non-blocking receive may read {@code ⊥}, Definition 3.3);</li>
 *   <li>{@code hb} — the spawn and join edges of JMC's process model, which are part of
 *       {@code porf} and of nothing else. Must's graphs have no spawn or join labels, so a
 *       backend must choose the endpoints among existing events and must do so
 *       <b>schedule-independently</b>, because {@code hb} is part of {@link GraphCanon}. The
 *       rule a backend must satisfy: a spawn edge runs from an event of the parent that is
 *       {@code porf}-after everything in the parent's causal past at the spawn — if the parent
 *       has no event of its own yet, from the {@code po}-maximal events of that causal past
 *       (possibly in other threads, possibly several edges) — to the child's first event; a
 *       join edge runs from the joined thread's last event to the joiner's next event. A
 *       backend with a static set of nodes needs no hb edges. A backend that does use them
 *       should be tested for producing the same {@link GraphCanon} under different schedules.
 *       Explicit {@code Spawn}/{@code Join} labels, so that the edges name real events, would
 *       be a cleaner design but do not exist;</li>
 *   <li>{@code ≤_G} — the order in which events were added.</li>
 * </ul>
 *
 * <p>{@code porf = (po ∪ rf ∪ hb)⁺} is computed once at construction and answered in O(1).
 *
 * <p>Immutable; built with a {@link Builder}. Holds no policy: which send a receive reads is the
 * backend's decision, recorded here after the fact.
 */
public final class ExecutionGraph {

    /**
     * A causal edge that is neither {@code po} nor {@code rf}: spawn or join.
     *
     * @param from the causally earlier event
     * @param to the causally later event
     */
    public record Edge(EventKey from, EventKey to) implements Comparable<Edge> {
        @Override
        public int compareTo(Edge o) {
            int c = from.compareTo(o.from);
            return c != 0 ? c : to.compareTo(o.to);
        }
    }

    /** Events per thread id, in program order. */
    private final TreeMap<Long, List<GraphEvent>> threads;

    /** Event keys in the order the events were added ({@code ≤_G}). */
    private final List<EventKey> insertionOrder;

    /** Reads-from: receive → the send it reads; receives reading {@code ⊥} are absent. */
    private final Map<EventKey, EventKey> rf;

    /** The receives that read {@code ⊥}. */
    private final Set<EventKey> bottoms;

    /** The spawn and join edges. */
    private final TreeSet<Edge> hb;

    /** Inverse of {@link #rf}: send → the receive that reads it. */
    private final Map<EventKey, EventKey> readerOf;

    /** For each event, the set of events that are {@code porf}-after it (transitive closure). */
    private final Map<EventKey, Set<EventKey>> porfSuccessors;

    /** Event → 0-based position in {@link #insertionOrder}. */
    private final Map<EventKey, Integer> insertionIndex;

    /** Copies the builder's contents and computes the derived indexes and the porf closure. */
    private ExecutionGraph(Builder b) {
        this.threads = new TreeMap<>();
        b.threads.forEach((t, l) -> threads.put(t, List.copyOf(l)));
        this.insertionOrder = List.copyOf(b.insertionOrder);
        this.rf = Collections.unmodifiableSortedMap(new TreeMap<>(b.rf));
        this.bottoms = Collections.unmodifiableSortedSet(new TreeSet<>(b.bottoms));
        this.hb = new TreeSet<>(b.hb);
        this.readerOf = new TreeMap<>();
        // Uniqueness of a send's reader is a well-formedness question (Definition 3.3), not ours;
        // on an ill-formed graph the LAST reader in key order wins, deterministically. rf is a
        // sorted map precisely so that no query here depends on hash iteration order.
        rf.forEach((r, s) -> readerOf.put(s, r));
        this.insertionIndex = new HashMap<>();
        for (int i = 0; i < insertionOrder.size(); i++) {
            insertionIndex.put(insertionOrder.get(i), i);
        }
        this.porfSuccessors = closure();
    }

    // ---- queries ----

    /**
     * Returns an event by key.
     *
     * @param key the key
     * @return the event
     * @throws IllegalArgumentException if absent
     */
    public GraphEvent event(EventKey key) {
        List<GraphEvent> l = threads.get(key.tid());
        if (l == null || key.idx() < 0 || key.idx() >= l.size()) {
            throw new IllegalArgumentException("no event " + key);
        }
        return l.get((int) key.idx());
    }

    /**
     * Whether the key names an event of this graph.
     *
     * @param key the key
     * @return {@code true} iff present
     */
    public boolean contains(EventKey key) {
        List<GraphEvent> l = threads.get(key.tid());
        return l != null && key.idx() >= 0 && key.idx() < l.size();
    }

    /**
     * Returns the events of a thread in program order.
     *
     * @param tid the thread
     * @return an unmodifiable list, empty if the thread has no events
     */
    public List<GraphEvent> thread(long tid) {
        return threads.getOrDefault(tid, List.of());
    }

    /**
     * Returns every thread's events.
     *
     * @return thread id → events in program order, ascending by id
     */
    public SortedMap<Long, List<GraphEvent>> threads() {
        return Collections.unmodifiableSortedMap(threads);
    }

    /**
     * Returns every event.
     *
     * @return the events in insertion order {@code ≤_G}
     */
    public List<GraphEvent> eventsInInsertionOrder() {
        List<GraphEvent> out = new ArrayList<>(insertionOrder.size());
        for (EventKey k : insertionOrder) {
            out.add(event(k));
        }
        return out;
    }

    /**
     * Returns the send events.
     *
     * @return the sends in insertion order
     */
    public List<GraphEvent> sends() {
        List<GraphEvent> out = new ArrayList<>();
        for (GraphEvent e : eventsInInsertionOrder()) {
            if (e.isSend()) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Returns the receive events.
     *
     * @return the receives in insertion order
     */
    public List<GraphEvent> receives() {
        List<GraphEvent> out = new ArrayList<>();
        for (GraphEvent e : eventsInInsertionOrder()) {
            if (e.isRecv()) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * The send a receive reads, if it reads a send.
     *
     * @param recv the receive
     * @return the send, or empty if the receive reads {@code ⊥} or has no rf recorded
     */
    public Optional<EventKey> rf(EventKey recv) {
        return Optional.ofNullable(rf.get(recv));
    }

    /**
     * Whether a receive reads {@code ⊥}.
     *
     * @param recv the receive
     * @return {@code true} iff recorded as reading {@code ⊥}
     */
    public boolean readsBottom(EventKey recv) {
        return bottoms.contains(recv);
    }

    /**
     * The receive that reads a send, if any.
     *
     * @param send the send
     * @return its reader, or empty if unread
     */
    public Optional<EventKey> readerOf(EventKey send) {
        return Optional.ofNullable(readerOf.get(send));
    }

    /**
     * Returns the extra causal edges.
     *
     * @return the spawn/join edges
     */
    public Set<Edge> hb() {
        return Collections.unmodifiableSet(hb);
    }

    /**
     * Program order: same thread, earlier index.
     *
     * @param a an event
     * @param b an event
     * @return {@code true} iff {@code a po b}
     */
    public boolean poBefore(EventKey a, EventKey b) {
        return a.tid() == b.tid() && a.idx() < b.idx();
    }

    /**
     * Causal order {@code porf = (po ∪ rf ∪ hb)⁺}.
     *
     * @param a an event
     * @param b an event
     * @return {@code true} iff {@code a porf b}
     */
    public boolean porfBefore(EventKey a, EventKey b) {
        Set<EventKey> s = porfSuccessors.get(a);
        return s != null && s.contains(b);
    }

    /**
     * Whether {@code porf} is irreflexive (no causal cycle) — Definition 3.3's last clause.
     *
     * @return {@code true} iff no event is {@code porf}-before itself
     */
    public boolean porfAcyclic() {
        for (Map.Entry<EventKey, Set<EventKey>> e : porfSuccessors.entrySet()) {
            if (e.getValue().contains(e.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Position in the insertion order {@code ≤_G}.
     *
     * @param key the event
     * @return its 0-based insertion index
     * @throws IllegalArgumentException if absent
     */
    public int insertionIndex(EventKey key) {
        Integer i = insertionIndex.get(key);
        if (i == null) {
            throw new IllegalArgumentException("no event " + key);
        }
        return i;
    }

    /**
     * Counts the events.
     *
     * @return the number of events
     */
    public int size() {
        return insertionOrder.size();
    }

    /**
     * The canonical form: what two runs must agree on to have produced the same graph.
     *
     * @return the canonical value
     */
    public GraphCanon canon() {
        TreeMap<Long, List<MpLabel>> labels = new TreeMap<>();
        threads.forEach((t, l) -> {
            List<MpLabel> ls = new ArrayList<>(l.size());
            for (GraphEvent e : l) {
                ls.add(e.label());
            }
            labels.put(t, Collections.unmodifiableList(ls));
        });
        return new GraphCanon(
                Collections.unmodifiableSortedMap(labels),
                Collections.unmodifiableSortedMap(new TreeMap<>(rf)),
                Collections.unmodifiableSortedSet(new TreeSet<>(bottoms)),
                Collections.unmodifiableSortedSet(hb));
    }

    // ---- porf closure ----

    /**
     * Computes the transitive closure of {@code po ∪ rf ∪ hb}: from every event, a depth-first
     * search over the successor edges (next event of the thread, the reader of a send, hb
     * targets).
     *
     * @return for each event, the set of events strictly {@code porf}-after it; an event is in
     *     its own set exactly when it lies on a causal cycle
     */
    private Map<EventKey, Set<EventKey>> closure() {
        Map<EventKey, List<EventKey>> succ = new HashMap<>();
        for (List<GraphEvent> l : threads.values()) {
            for (int i = 0; i + 1 < l.size(); i++) {
                succ.computeIfAbsent(l.get(i).key(), k -> new ArrayList<>()).add(l.get(i + 1).key());
            }
        }
        rf.forEach((r, s) -> succ.computeIfAbsent(s, k -> new ArrayList<>()).add(r));
        for (Edge e : hb) {
            succ.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
        }
        Map<EventKey, Set<EventKey>> out = new HashMap<>();
        for (EventKey start : insertionOrder) {
            Set<EventKey> seen = new HashSet<>();
            Deque<EventKey> stack = new ArrayDeque<>(succ.getOrDefault(start, List.of()));
            while (!stack.isEmpty()) {
                EventKey k = stack.pop();
                if (seen.add(k)) {
                    stack.addAll(succ.getOrDefault(k, List.of()));
                }
            }
            out.put(start, seen);
        }
        return out;
    }

    @Override
    public String toString() {
        return "ExecutionGraph" + canon();
    }

    // ---- builder ----

    /**
     * Builds an {@link ExecutionGraph} event by event. Events of a thread must be added in
     * program order; reads-from and {@code ⊥} reads are recorded per receive; spawn/join edges per
     * pair. Nothing here checks consistency — that is
     * {@code org.mpi_sws.jmc.mp.consistency.MpConsistency}'s job — but structural nonsense is
     * rejected at once: an event out of program order, an rf or a {@code ⊥} read on a
     * non-receive, an rf from a non-send, an hb endpoint that is not an event. An hb self-loop is
     * accepted here and surfaces as a Definition 3.3 causal cycle.
     */
    public static final class Builder {
        private final TreeMap<Long, List<GraphEvent>> threads = new TreeMap<>();
        private final List<EventKey> insertionOrder = new ArrayList<>();
        private final Map<EventKey, EventKey> rf = new HashMap<>();
        private final Set<EventKey> bottoms = new HashSet<>();
        private final Set<Edge> hb = new HashSet<>();

        /** Creates an empty builder. */
        public Builder() {}

        /**
         * Creates a builder pre-loaded with a graph's contents — the way a backend extends a
         * prefix.
         *
         * @param g the graph to start from
         */
        public Builder(ExecutionGraph g) {
            g.threads.forEach((t, l) -> threads.put(t, new ArrayList<>(l)));
            insertionOrder.addAll(g.insertionOrder);
            rf.putAll(g.rf);
            bottoms.addAll(g.bottoms);
            hb.addAll(g.hb);
        }

        /**
         * Appends an event as the next event of its thread.
         *
         * @param e the event; its key's index must equal the thread's current length
         * @return this
         * @throws IllegalArgumentException if the index is not the next in program order
         */
        public Builder add(GraphEvent e) {
            Objects.requireNonNull(e, "event");
            List<GraphEvent> l = threads.computeIfAbsent(e.key().tid(), t -> new ArrayList<>());
            if (e.key().idx() != l.size()) {
                throw new IllegalArgumentException(
                        "event " + e.key() + " is not next in thread " + e.key().tid()
                                + " (expected idx " + l.size() + ")");
            }
            l.add(e);
            insertionOrder.add(e.key());
            return this;
        }

        /**
         * Records that a receive reads a send.
         *
         * @param recv the receive (must be present and a receive)
         * @param send the send (must be present and a send)
         * @return this
         */
        public Builder readsFrom(EventKey recv, EventKey send) {
            requireLabel(recv, MpLabel.Recv.class, "receive");
            requireLabel(send, MpLabel.Send.class, "send");
            bottoms.remove(recv);
            rf.put(recv, send);
            return this;
        }

        /**
         * Records that a receive reads {@code ⊥}.
         *
         * @param recv the receive (must be present and a receive)
         * @return this
         */
        public Builder readsBottom(EventKey recv) {
            requireLabel(recv, MpLabel.Recv.class, "receive");
            rf.remove(recv);
            bottoms.add(recv);
            return this;
        }

        /**
         * Adds a spawn or join edge.
         *
         * @param from the causally earlier event
         * @param to the causally later event
         * @return this
         */
        public Builder hb(EventKey from, EventKey to) {
            require(from);
            require(to);
            hb.add(new Edge(from, to));
            return this;
        }

        /**
         * Builds the graph.
         *
         * @return the immutable graph
         */
        public ExecutionGraph build() {
            return new ExecutionGraph(this);
        }

        private void require(EventKey k) {
            List<GraphEvent> l = threads.get(k.tid());
            if (l == null || k.idx() < 0 || k.idx() >= l.size()) {
                throw new IllegalArgumentException("no event " + k);
            }
        }

        private void requireLabel(EventKey k, Class<? extends MpLabel> cls, String what) {
            require(k);
            MpLabel label = threads.get(k.tid()).get((int) k.idx()).label();
            if (!cls.isInstance(label)) {
                throw new IllegalArgumentException(k + " is not a " + what + ": " + label);
            }
        }
    }
}
