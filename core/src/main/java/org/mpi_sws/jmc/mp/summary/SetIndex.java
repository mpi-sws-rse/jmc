package org.mpi_sws.jmc.mp.summary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set index of one side's compact summaries and its {@code covers} query. It is a Java port of
 * the {@code conformance::compact::SetIndex} of the TraceForge tool.
 *
 * <p>Summaries are grouped by <b>signature</b>: the statuses and the words with their values. There
 * is one slot per distinct signature, found by equality. A slot holds each distinct order (the
 * counts of a {@link Summary}) once, in admission order, mapped to its admission number
 * {@code seq}.
 * {@link #insert} refuses an exact duplicate without advancing {@code seq}.
 *
 * <p>{@link #covers} answers {@link Covers.NoSig} when no slot has the query's signature.
 * Otherwise it first runs the <b>exact-hit probe</b>: if the slot holds the query's own counts, the
 * answer is {@link Covers.Found} with that order's {@code seq} and no pointwise test is made.
 * If not, the held orders are scanned in admission order, and the first order with
 * {@code held ≤ query} pointwise (the test stops at the first coordinate that exceeds) answers
 * {@link Covers.Found}. If there is none, the answer is {@link Covers.NotFound}. The index keeps no
 * antichain, never removes an order and has no capacity bound.
 *
 * <p>Differences from the TraceForge structure that do not change the answers: slots are found
 * through a hash map keyed by the full signature (TraceForge uses a shape-hash bucket and a
 * render-hash hint, so there is no counterpart of its {@code NoBucket} answer here), and counts are
 * plain integers rather than {@code U8}/{@code U16}. The counters follow the definitions of
 * TraceForge's {@code SetStats}.
 *
 * <p>Not thread-safe; a query advances counters.
 */
public final class SetIndex {

    /**
     * A summary's signature: its visible roles, statuses and words (the encoded values). Two
     * summaries share a slot iff their signatures are equal.
     *
     * @param roles the visible roles in the format's order
     * @param statuses role → status
     * @param words role → the encoded observations in program order
     */
    public record Signature(
            List<String> roles, Map<String, String> statuses, Map<String, List<String>> words) {

        /**
         * The signature of a summary.
         *
         * @param s the summary
         * @return its signature
         */
        public static Signature of(Summary s) {
            return new Signature(s.roles(), s.statuses(), s.words());
        }
    }

    /** The outcome of an insertion. */
    public sealed interface Inserted {
        /**
         * The order was stored.
         *
         * @param seq its admission number
         */
        record Stored(long seq) implements Inserted {}

        /** The slot already held this order; nothing stored, {@code seq} not advanced. */
        record Duplicate() implements Inserted {}
    }

    /** The answer of a query. */
    public sealed interface Covers {
        /** No slot has the query's signature. */
        record NoSig() implements Covers {}

        /**
         * A held order of the query's signature is pointwise {@code ≤} the query's.
         *
         * @param seq the admission number of the witness: the query's own order on an exact hit,
         *     otherwise the first in admission order
         * @param exact whether the exact-hit probe answered
         */
        record Found(long seq, boolean exact) implements Covers {}

        /** The signature has a slot, but no held order is {@code ≤} the query's. */
        record NotFound() implements Covers {}
    }

    /**
     * One held order with its slot.
     *
     * @param sigId the slot's id, in slot-creation order
     * @param signature the slot's signature
     * @param seq the order's admission number
     * @param counts the order
     */
    public record Entry(int sigId, Signature signature, long seq, List<Integer> counts) {}

    /**
     * The index's counters, defined as in TraceForge's {@code SetStats} where it has a counterpart.
     *
     * @param entries held orders
     * @param signatures slots (each holds at least one order; nothing is removed)
     * @param slotMax the largest number of orders one slot holds
     * @param admitted insertions that stored an order
     * @param duplicates insertions refused as exact duplicates
     * @param containmentTests {@code ≤} tests started by queries
     * @param coordinatesCompared coordinates read by the queries' {@code ≤} tests
     * @param exactHits queries answered by the exact-hit probe
     */
    public record Stats(
            int entries,
            int signatures,
            int slotMax,
            long admitted,
            long duplicates,
            long containmentTests,
            long coordinatesCompared,
            long exactHits) {}

    private static final class Slot {
        final int sigId;
        final LinkedHashMap<List<Integer>, Long> orders = new LinkedHashMap<>();

        Slot(int sigId) {
            this.sigId = sigId;
        }
    }

    private final LinkedHashMap<Signature, Slot> slots = new LinkedHashMap<>();
    private long nextSeq;
    private int nextSigId;
    private int entries;
    private int slotMax;
    private long admitted;
    private long duplicates;
    private long containmentTests;
    private long coordinatesCompared;
    private long exactHits;

    /** Creates an empty index. */
    public SetIndex() {}

    /**
     * Inserts a summary: its signature's slot is created when absent (with the next {@code sigId});
     * an order the slot already holds is refused; otherwise it is stored with the next {@code seq}.
     *
     * @param s the summary
     * @return the outcome
     */
    public Inserted insert(Summary s) {
        Slot slot = slots.computeIfAbsent(Signature.of(s), k -> new Slot(nextSigId++));
        List<Integer> ord = List.copyOf(s.counts());
        if (slot.orders.containsKey(ord)) {
            duplicates++;
            return new Inserted.Duplicate();
        }
        long seq = nextSeq++;
        slot.orders.put(ord, seq);
        admitted++;
        entries++;
        slotMax = Math.max(slotMax, slot.orders.size());
        return new Inserted.Stored(seq);
    }

    /**
     * The read-only query (TraceForge's {@code SetIndex::covers}). Asks whether some held summary
     * with the query's signature is pointwise {@code ≤} the query. Only the statistics counters are
     * updated.
     *
     * @param s the query
     * @return the answer
     */
    public Covers covers(Summary s) {
        Slot slot = slots.get(Signature.of(s));
        if (slot == null) {
            return new Covers.NoSig();
        }
        List<Integer> q = s.counts();
        Long exact = slot.orders.get(q);
        if (exact != null) {
            exactHits++;
            return new Covers.Found(exact, true);
        }
        for (Map.Entry<List<Integer>, Long> e : slot.orders.entrySet()) {
            containmentTests++;
            if (le(e.getKey(), q)) {
                return new Covers.Found(e.getValue(), false);
            }
        }
        return new Covers.NotFound();
    }

    /**
     * Tests {@code a ≤ b} pointwise, exiting at the first coordinate where {@code a} exceeds
     * {@code b} (TraceForge's {@code le_counts}). The coordinates read are counted.
     *
     * @param a the held order
     * @param b the query's order
     * @return whether {@code a} is pointwise at most {@code b}
     * @throws IllegalStateException if the two lists differ in length
     */
    private boolean le(List<Integer> a, List<Integer> b) {
        if (a.size() != b.size()) {
            throw new IllegalStateException(
                    "counts of one signature differ in length: " + a.size() + " vs " + b.size());
        }
        for (int i = 0; i < a.size(); i++) {
            coordinatesCompared++;
            if (a.get(i) > b.get(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every held order: slots in creation order, orders in admission order.
     *
     * @return the entries
     */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(entries);
        for (Map.Entry<Signature, Slot> se : slots.entrySet()) {
            Slot slot = se.getValue();
            for (Map.Entry<List<Integer>, Long> oe : slot.orders.entrySet()) {
                out.add(new Entry(slot.sigId, se.getKey(), oe.getValue(), oe.getKey()));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Held orders.
     *
     * @return the number of distinct summaries held
     */
    public int size() {
        return entries;
    }

    /**
     * The counters.
     *
     * @return a snapshot
     */
    public Stats stats() {
        return new Stats(
                entries, slots.size(), slotMax, admitted, duplicates, containmentTests,
                coordinatesCompared, exactHits);
    }
}
