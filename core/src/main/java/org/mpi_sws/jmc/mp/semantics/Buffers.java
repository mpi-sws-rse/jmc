package org.mpi_sws.jmc.mp.semantics;

import org.mpi_sws.jmc.mp.consistency.VectorClock;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The pending messages of every communication model, each in a canonical form so that two
 * buffer values are {@code equals} exactly when they are behaviourally the same (which is what
 * state merging in the explicit-state backend depends on):
 *
 * <ul>
 *   <li>{@code ASYN} — a multiset per destination, stored sorted by {@link Envelope#CANONICAL}
 *       (order-insensitive);</li>
 *   <li>{@code P2P} — a FIFO queue per {@code (sender, destination)} in emission order; the
 *       relative order of different senders' messages is not semantic and is not stored;</li>
 *   <li>{@code CD} — a bag per destination, stored sorted; every entry carries its stamp;</li>
 *   <li>{@code MBOX} — one FIFO list per destination in <em>global emission order</em>. Only the
 *       relative order is stored: an absolute sequence number would be a monotone counter in the
 *       state and would defeat merging for every {@code mbox} program.</li>
 * </ul>
 *
 * <p>Buffers are disjoint per model (Definition 3.8 of the Must paper: consistency is checked per
 * model on that model's events); clocks are shared across models ({@link Pending}).
 *
 * <p><b>What equality is.</b> {@link #equals} and {@link #hashCode} are defined over the
 * <em>projection</em> of each pending entry to {@code (value, clock)} — as a multiset for the asyn
 * and cd bags, as a sequence for the p2p channels and mbox lists, each under its own map key —
 * plus the clock mode. That is exactly what the delivery rules and a receiving handler can
 * observe:
 * the destination and model are the map keys; the sender is the p2p channel key (per-sender FIFO
 * is semantic there) and, with clocks, is encoded in the stamp (two senders' stamps never
 * coincide); under asyn and mbox without clocks the sender is <em>unobservable</em> — a handler
 * receives the value only — so two buffers holding the same values from different senders are the
 * same state. {@link Envelope#sendIndex()} is deliberately <em>not</em> part of equality either:
 * the rules never read it, and it is a per-sender monotone counter, so including it would make
 * every pass through a cycle with an in-flight message a fresh state and defeat merging. Both
 * stay in storage because {@link #remove}, the canonical result order of {@link Delivery} and
 * any recording of which send a receive read need them.
 *
 * <p><b>Clock mode.</b> A buffer is created either {@link #withClocks() with} or {@link
 * #withoutClocks() without} clocks, from the program's declared model set (clocks iff it uses
 * {@code cd}). {@link #emit} enforces the whole stamping invariant: with
 * clocks, every emission of every model must carry a stamp; without, none may, and a {@code cd}
 * emission is rejected outright. A backend that forgets to stamp therefore fails loudly here, once,
 * whichever backend it is.
 *
 * <p>Immutable: {@link #emit} and {@link #remove} return new values. Holds no policy — it is a
 * data structure; the delivery rules live in {@link Delivery}.
 */
public final class Buffers {

    /**
     * A {@code (sender, destination)} channel key, ordered by sender then destination. It names
     * one {@code P2P} FIFO queue.
     *
     * @param src the sender
     * @param dst the destination
     */
    public record Channel(Tid src, Tid dst) implements Comparable<Channel> {
        @Override
        public int compareTo(Channel o) {
            int c = src.compareTo(o.src);
            return c != 0 ? c : dst.compareTo(o.dst);
        }
    }

    /** The shared empty value with clocks enabled. */
    private static final Buffers EMPTY_WITH_CLOCKS = new Buffers(
            true, new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>());
    /** The shared empty value with clocks disabled. */
    private static final Buffers EMPTY_WITHOUT_CLOCKS = new Buffers(
            false, new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>());

    /** Whether emissions must carry a clock stamp (true iff the program uses {@code cd}). */
    private final boolean clocksEnabled;

    /** Pending {@code ASYN} messages per destination, sorted by {@link Pending#CANONICAL}. */
    private final TreeMap<Tid, List<Pending>> asyn;

    /** Pending {@code P2P} messages per channel, in emission order. */
    private final TreeMap<Channel, List<Pending>> p2p;

    /** Pending {@code CD} messages per destination, sorted by {@link Pending#CANONICAL}. */
    private final TreeMap<Tid, List<Pending>> cd;

    /** Pending {@code MBOX} messages per destination, in global emission order. */
    private final TreeMap<Tid, List<Pending>> mbox;

    private Buffers(
            boolean clocksEnabled,
            TreeMap<Tid, List<Pending>> asyn,
            TreeMap<Channel, List<Pending>> p2p,
            TreeMap<Tid, List<Pending>> cd,
            TreeMap<Tid, List<Pending>> mbox) {
        this.clocksEnabled = clocksEnabled;
        this.asyn = asyn;
        this.p2p = p2p;
        this.cd = cd;
        this.mbox = mbox;
    }

    /**
     * Returns empty buffers for a program that uses {@code cd} somewhere: every emission of every
     * model must be stamped.
     *
     * @return the empty buffers, clocks enabled
     */
    public static Buffers withClocks() {
        return EMPTY_WITH_CLOCKS;
    }

    /**
     * Returns empty buffers for a program that uses no {@code cd} at all: no emission may be
     * stamped and no {@code cd} send may be emitted.
     *
     * @return the empty buffers, clocks disabled
     */
    public static Buffers withoutClocks() {
        return EMPTY_WITHOUT_CLOCKS;
    }

    /**
     * Returns empty buffers in the given clock mode.
     *
     * @param clocksEnabled whether the program uses {@code cd} anywhere
     * @return the empty buffers
     */
    public static Buffers empty(boolean clocksEnabled) {
        return clocksEnabled ? EMPTY_WITH_CLOCKS : EMPTY_WITHOUT_CLOCKS;
    }

    /**
     * Whether this buffer stamps and reads clocks.
     *
     * @return {@code true} iff the program uses {@code cd}
     */
    public boolean clocksEnabled() {
        return clocksEnabled;
    }

    /**
     * Appends an emitted message to its model's buffer.
     *
     * @param envelope the message
     * @param stamp the sender's clock at emission, required iff clocks are enabled
     * @return the new buffers
     * @throws IllegalArgumentException if the stamp contradicts the clock mode (an unstamped
     *     emission of <em>any</em> model with clocks enabled; a stamped emission, or any
     *     {@code cd} emission, with clocks disabled) — a backend that gets this wrong must fail
     *     here rather than admit deliveries Must forbids; or if a send with the same
     *     {@code (src, sendIndex)} is already pending, which can only be a backend bug (a message
     *     emitted twice, or a send index counted twice)
     * @implNote The checks run in the order stated: clock mode against the stamp first, then the
     *     {@code cd}-without-clocks rule, then the duplicate check, so a stamped {@code cd} send
     *     under disabled clocks is reported as a stamp error. The duplicate check covers
     *     <em>pending</em> sends only. Whether an index may be reused after its send was delivered
     *     is the backend's invariant, not the buffer's: the explicit-state backend re-derives
     *     indices per state and may reuse them; strategies running inside the JMC runtime count
     *     monotonically per iteration and must not. A delivered-index set in the buffer would
     *     detect the latter but would be a monotone counter in the state, which is the very thing
     *     {@link #equals} exists to keep out, so it is deliberately absent. An execution graph
     *     rejects a repeated {@code EventKey} instead.
     */
    public Buffers emit(Envelope envelope, VectorClock stamp) {
        Objects.requireNonNull(envelope, "envelope");
        if (clocksEnabled && stamp == null) {
            throw new IllegalArgumentException(
                    "clocks enabled but " + envelope + " emitted without a stamp");
        }
        if (!clocksEnabled && stamp != null) {
            throw new IllegalArgumentException(
                    "clocks disabled but " + envelope + " emitted with a stamp");
        }
        if (!clocksEnabled && envelope.model() == CommunicationModel.CD) {
            throw new IllegalArgumentException(
                    "cd send " + envelope + " emitted with clocks disabled");
        }
        if (isPending(envelope.src(), envelope.sendIndex())) {
            throw new IllegalArgumentException("already pending: " + envelope);
        }
        Pending p = new Pending(envelope, stamp);
        return switch (envelope.model()) {
            case ASYN -> new Buffers(clocksEnabled, putSorted(asyn, envelope.dst(), p), p2p, cd, mbox);
            case P2P -> new Buffers(
                    clocksEnabled, asyn,
                    putFifo(p2p, new Channel(envelope.src(), envelope.dst()), p), cd, mbox);
            case CD -> new Buffers(clocksEnabled, asyn, p2p, putSorted(cd, envelope.dst(), p), mbox);
            case MBOX -> new Buffers(clocksEnabled, asyn, p2p, cd, putFifo(mbox, envelope.dst(), p));
        };
    }

    /**
     * Whether a send with this identity is pending in any model's buffer.
     *
     * @param src the sender
     * @param sendIndex the sender's send index
     * @return {@code true} iff such a message is pending
     */
    private boolean isPending(Tid src, long sendIndex) {
        for (List<Pending> l : asyn.values()) {
            if (contains(l, src, sendIndex)) {
                return true;
            }
        }
        for (List<Pending> l : p2p.values()) {
            if (contains(l, src, sendIndex)) {
                return true;
            }
        }
        for (List<Pending> l : cd.values()) {
            if (contains(l, src, sendIndex)) {
                return true;
            }
        }
        for (List<Pending> l : mbox.values()) {
            if (contains(l, src, sendIndex)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the list holds a message with the given sender and send index. */
    private static boolean contains(List<Pending> list, Tid src, long sendIndex) {
        for (Pending p : list) {
            if (p.envelope().src().equals(src) && p.envelope().sendIndex() == sendIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a delivered message from its model's buffer.
     *
     * @param envelope the message that was delivered
     * @return the new buffers
     * @throws IllegalArgumentException if the message is not pending
     */
    public Buffers remove(Envelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        return switch (envelope.model()) {
            case ASYN -> new Buffers(clocksEnabled, drop(asyn, envelope.dst(), envelope), p2p, cd, mbox);
            case P2P -> new Buffers(
                    clocksEnabled, asyn,
                    drop(p2p, new Channel(envelope.src(), envelope.dst()), envelope), cd, mbox);
            case CD -> new Buffers(clocksEnabled, asyn, p2p, drop(cd, envelope.dst(), envelope), mbox);
            case MBOX -> new Buffers(clocksEnabled, asyn, p2p, cd, drop(mbox, envelope.dst(), envelope));
        };
    }

    /**
     * The messages pending for a destination under a model, in that model's canonical order.
     * For {@code P2P} this concatenates the per-sender queues in sender order, each queue in
     * emission order.
     *
     * @param model the model
     * @param dst the destination
     * @return an unmodifiable list, empty if nothing is pending
     */
    public List<Pending> pending(CommunicationModel model, Tid dst) {
        return switch (model) {
            case ASYN -> view(asyn.get(dst));
            case CD -> view(cd.get(dst));
            case MBOX -> view(mbox.get(dst));
            case P2P -> pendingP2pTo(dst);
        };
    }

    /** All {@code P2P} messages to {@code dst}: per-sender queues in sender order. */
    private List<Pending> pendingP2pTo(Tid dst) {
        List<Pending> all = new ArrayList<>();
        for (Map.Entry<Channel, List<Pending>> e : p2p.entrySet()) {
            if (e.getKey().dst().equals(dst)) {
                all.addAll(e.getValue());
            }
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * The messages pending on one {@code P2P} channel, in emission order.
     *
     * @param src the sender
     * @param dst the destination
     * @return an unmodifiable list, empty if nothing is pending
     */
    public List<Pending> pendingP2p(Tid src, Tid dst) {
        return view(p2p.get(new Channel(src, dst)));
    }

    /**
     * Whether nothing is pending under any model.
     *
     * @return {@code true} iff every buffer is empty
     */
    public boolean isEmpty() {
        return asyn.isEmpty() && p2p.isEmpty() && cd.isEmpty() && mbox.isEmpty();
    }

    /**
     * Counts the pending messages across all models.
     *
     * @return the total
     */
    public int size() {
        int n = 0;
        for (List<Pending> l : asyn.values()) {
            n += l.size();
        }
        for (List<Pending> l : p2p.values()) {
            n += l.size();
        }
        for (List<Pending> l : cd.values()) {
            n += l.size();
        }
        for (List<Pending> l : mbox.values()) {
            n += l.size();
        }
        return n;
    }

    // ---- internals: every mutation copies; maps never hold empty lists ----

    /** Returns a copy of the map with {@code p} inserted into the key's list in sorted order. */
    private static <K> TreeMap<K, List<Pending>> putSorted(
            TreeMap<K, List<Pending>> map, K key, Pending p) {
        TreeMap<K, List<Pending>> copy = new TreeMap<>(map);
        List<Pending> list = new ArrayList<>(map.getOrDefault(key, List.of()));
        int i = Collections.binarySearch(list, p, Pending.CANONICAL);
        list.add(i < 0 ? -i - 1 : i, p);
        copy.put(key, Collections.unmodifiableList(list));
        return copy;
    }

    /** Returns a copy of the map with {@code p} appended to the key's list. */
    private static <K> TreeMap<K, List<Pending>> putFifo(
            TreeMap<K, List<Pending>> map, K key, Pending p) {
        TreeMap<K, List<Pending>> copy = new TreeMap<>(map);
        List<Pending> list = new ArrayList<>(map.getOrDefault(key, List.of()));
        list.add(p);
        copy.put(key, Collections.unmodifiableList(list));
        return copy;
    }

    /**
     * Returns a copy of the map with the entry for {@code envelope} removed from the key's list
     * (dropping the key if its list becomes empty).
     *
     * @throws IllegalArgumentException if the envelope is not in the key's list
     */
    private static <K> TreeMap<K, List<Pending>> drop(
            TreeMap<K, List<Pending>> map, K key, Envelope envelope) {
        List<Pending> list = map.get(key);
        int i = -1;
        if (list != null) {
            for (int j = 0; j < list.size(); j++) {
                if (list.get(j).envelope().equals(envelope)) {
                    i = j;
                    break;
                }
            }
        }
        if (i < 0) {
            throw new IllegalArgumentException("not pending: " + envelope);
        }
        TreeMap<K, List<Pending>> copy = new TreeMap<>(map);
        List<Pending> rest = new ArrayList<>(list);
        rest.remove(i);
        if (rest.isEmpty()) {
            copy.remove(key);
        } else {
            copy.put(key, Collections.unmodifiableList(rest));
        }
        return copy;
    }

    /** Returns the list, or an empty list for a missing key. */
    private static List<Pending> view(List<Pending> list) {
        return list == null ? List.of() : list;
    }

    // ---- behavioural equality: the (src, dst, model, value, clock) projection ----

    /**
     * The part of a pending entry the delivery rules and the receiving handler can observe. The
     * sender, destination and model are not here: destination and model are the map keys of the
     * structure holding the entry, the sender is the p2p channel key where it is semantic and is
     * otherwise unobservable (or encoded in the stamp when clocks are on); {@code sendIndex} is
     * storage only.
     */
    private record Key(MpMessage value, VectorClock clock) {
        static Key of(Pending p) {
            return new Key(p.envelope().value(), p.clock());
        }
    }

    /** A bag of projections: key → multiplicity. */
    private static Map<Key, Integer> bag(List<Pending> list) {
        Map<Key, Integer> m = new HashMap<>();
        for (Pending p : list) {
            m.merge(Key.of(p), 1, Integer::sum);
        }
        return m;
    }

    /** A sequence of projections. */
    private static List<Key> seq(List<Pending> list) {
        List<Key> out = new ArrayList<>(list.size());
        for (Pending p : list) {
            out.add(Key.of(p));
        }
        return out;
    }

    /** Maps every key of the buffer map to the bag of its projections. */
    private static <K> Map<K, Map<Key, Integer>> bags(TreeMap<K, List<Pending>> m) {
        Map<K, Map<Key, Integer>> out = new HashMap<>();
        m.forEach((k, v) -> out.put(k, bag(v)));
        return out;
    }

    /** Maps every key of the buffer map to the sequence of its projections. */
    private static <K> Map<K, List<Key>> seqs(TreeMap<K, List<Pending>> m) {
        Map<K, List<Key>> out = new HashMap<>();
        m.forEach((k, v) -> out.put(k, seq(v)));
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Buffers b)) {
            return false;
        }
        return clocksEnabled == b.clocksEnabled
                && bags(asyn).equals(bags(b.asyn))
                && seqs(p2p).equals(seqs(b.p2p))
                && bags(cd).equals(bags(b.cd))
                && seqs(mbox).equals(seqs(b.mbox));
    }

    @Override
    public int hashCode() {
        return Objects.hash(clocksEnabled, bags(asyn), seqs(p2p), bags(cd), seqs(mbox));
    }

    @Override
    public String toString() {
        return "Buffers{clocks=" + clocksEnabled + ", asyn=" + asyn + ", p2p=" + p2p
                + ", cd=" + cd + ", mbox=" + mbox + "}";
    }
}
