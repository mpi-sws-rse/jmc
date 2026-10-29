package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tester tests for J7 round 02 (plan/jmc-must/log/dev/J7/round-02.prompt.md): the {@code covers} unit
 * cases of the tester's derivation (plan/jmc-must/log/dev/J7/derivations.md §1), derived by hand from
 * TraceForge f9baaba {@code compact.rs} {@code SetIndex::insert}/{@code covers} before the Java was read.
 * Every expected answer, witness {@code seq} and counter triple (exactHits, containmentTests,
 * coordinatesCompared) is the derivation's.
 */
class SetIndexTest {

    private static final List<String> V2 = List.of("c0", "c1");
    private static final String WORDS_W = "{\"c0\":[{\"s\":{\"t\":\"Acq\",\"f\":[0]}},{\"r\":{\"t\":\"Grant\",\"f\":"
            + "[0,0]}},"
            + "{\"s\":{\"t\":\"Rel\",\"f\":[0]}}],\"c1\":[{\"s\":{\"t\":\"Acq\",\"f\":[1]}},"
            + "{\"r\":{\"t\":\"Grant\",\"f\":[1,0]}},{\"s\":{\"t\":\"Rel\",\"f\":[1]}}]}";
    private static final String WORDS_WP = WORDS_W.replace("\"f\":[1,0]", "\"f\":[1,1]");
    private static final String DONE2 = "{\"c0\":\"done\",\"c1\":\"done\"}";

    static final String Z = "0,0,0,0,0,0";
    static final String A = "0,0,0,0,3,3";
    static final String P = "0,3,3,0,0,0";
    static final String Q = "0,0,1,0,3,3";
    static final String N = "0,0,0,0,2,2";
    static final String D = "0,0,0,0,1,1";

    static Summary line(String statuses, String words, String counts, List<String> visible) {
        return SpecificationFile.parseLine(
                "{\"statuses\":" + statuses + ",\"words\":" + words + ",\"counts\":[" + counts + "],\"n\":1}", visible);
    }

    static Summary inW(String counts) {
        return line(DONE2, WORDS_W, counts, V2);
    }

    static Summary wp(String counts) {
        return line(DONE2, WORDS_WP, counts, V2);
    }

    static SetIndex held(Summary... s) {
        SetIndex ix = new SetIndex();
        for (Summary x : s) {
            assertInstanceOf(SetIndex.Inserted.Stored.class, ix.insert(x));
        }
        return ix;
    }

    static void found(SetIndex.Covers c, long seq, boolean exact) {
        assertEquals(new SetIndex.Covers.Found(seq, exact), c);
    }

    static void counters(SetIndex ix, long exactHits, long tests, long coords) {
        SetIndex.Stats s = ix.stats();
        assertEquals(List.of(exactHits, tests, coords),
                List.of(s.exactHits(), s.containmentTests(), s.coordinatesCompared()), "(exactHits, tests, coords)");
    }

    // ------------------------------------------------------------------ §1a order cases

    @Test
    void c1a_exactHitBeatsEarlierLe() {
        SetIndex ix = held(inW(Z), inW(A));
        found(ix.covers(inW(A)), 1, true);
        counters(ix, 1, 0, 0);
    }

    @Test
    void c1b_exactHitWithOtherHeldBefore() {
        SetIndex ix = held(inW(P), inW(A));
        found(ix.covers(inW(A)), 1, true);
        counters(ix, 1, 0, 0);
    }

    @Test
    void c2_heldLeQueryStrict() {
        SetIndex ix = held(inW(A));
        found(ix.covers(inW(Q)), 0, false);
        counters(ix, 0, 1, 6);
    }

    @Test
    void c3_queryLeHeld_notFound_exitAfter3Of6() {
        SetIndex ix = held(inW(Q));
        assertEquals(new SetIndex.Covers.NotFound(), ix.covers(inW(A)));
        counters(ix, 0, 1, 3);
    }

    @Test
    void c4_incomparable_notFound() {
        SetIndex ix = held(inW(P));
        assertEquals(new SetIndex.Covers.NotFound(), ix.covers(inW(A)));
        counters(ix, 0, 1, 2);
    }

    @Test
    void c4b_doubleGrantAgainstSpec_notFound() {
        SetIndex ix = held(inW(A), inW(P));
        assertEquals(new SetIndex.Covers.NotFound(), ix.covers(inW(D)));
        counters(ix, 0, 2, 7);
    }

    @Test
    void c5_onlySecondAdmittedLe() {
        SetIndex ix = held(inW(P), inW(A));
        found(ix.covers(inW(Q)), 1, false);
        counters(ix, 0, 2, 8);
    }

    @Test
    void c6_bothLe_earlierAdmittedWins() {
        SetIndex ix = held(inW(Z), inW(A));
        found(ix.covers(inW(Q)), 0, false);
        counters(ix, 0, 1, 6);
        assertEquals(List.of(0, 0, 0, 0, 0, 0), witness(ix, 0));
    }

    @Test
    void c6r_bothLe_reversed_witnessIsA() {
        SetIndex ix = held(inW(A), inW(Z));
        found(ix.covers(inW(Q)), 0, false);
        counters(ix, 0, 1, 6);
        assertEquals(List.of(0, 0, 0, 0, 3, 3), witness(ix, 0));
    }

    @Test
    void n1_notFound_orderPthenA() {
        SetIndex ix = held(inW(P), inW(A));
        assertEquals(new SetIndex.Covers.NotFound(), ix.covers(inW(N)));
        counters(ix, 0, 2, 7);
    }

    @Test
    void n2_notFound_orderAthenP() {
        SetIndex ix = held(inW(A), inW(P));
        assertEquals(new SetIndex.Covers.NotFound(), ix.covers(inW(N)));
        counters(ix, 0, 2, 7);
    }

    @Test
    void t1_oneRole_emptyCounts_exact() {
        String words = "{\"c0\":[{\"s\":{\"t\":\"Put\",\"f\":[0,0,0]}},{\"r\":{\"t\":\"Ack\",\"f\":[]}},"
                + "{\"s\":{\"t\":\"Get\",\"f\":[0,0]}},{\"r\":{\"t\":\"Val\",\"f\":[0]}}]}";
        Summary s = line("{\"c0\":\"done\"}", words, "", List.of("c0"));
        assertEquals(List.of(), s.counts());
        SetIndex ix = held(s);
        found(ix.covers(s), 0, true);
        counters(ix, 1, 0, 0);
    }

    static List<Integer> witness(SetIndex ix, long seq) {
        for (SetIndex.Entry e : ix.entries()) {
            if (e.seq() == seq) {
                return e.counts();
            }
        }
        throw new AssertionError("no entry with seq " + seq);
    }

    // ------------------------------------------------------------------ §1b signature cases

    static void noSig(SetIndex ix, Summary q) {
        assertEquals(new SetIndex.Covers.NoSig(), ix.covers(q));
        counters(ix, 0, 0, 0);
    }

    @Test
    void g1_valueDiffers_noSig() {
        noSig(held(inW(A)), wp(A));
    }

    @Test
    void g2_statusDiffers_noSig() {
        noSig(held(inW(A)), line("{\"c0\":\"done\",\"c1\":\"blocked\"}", WORDS_W, A, V2));
    }

    @Test
    void g3_bottomVsValue_noSig_bothWays() {
        List<String> ab = List.of("a", "b");
        Summary bottom = line("{\"a\":\"done\",\"b\":\"done\"}", "{\"a\":[{\"r\":null}],\"b\":[{\"s\":1}]}", "0,0", ab);
        Summary value = line("{\"a\":\"done\",\"b\":\"done\"}", "{\"a\":[{\"r\":1}],\"b\":[{\"s\":1}]}", "1,0", ab);
        noSig(held(bottom), value);
        noSig(held(value), bottom);
    }

    @Test
    void g4_sameWordsOnSwappedRoles_noSig() {
        List<String> ab = List.of("a", "b");
        Summary h = line("{\"a\":\"done\",\"b\":\"done\"}", "{\"a\":[{\"s\":1}],\"b\":[{\"s\":2}]}", "0,0", ab);
        Summary q = line("{\"a\":\"done\",\"b\":\"done\"}", "{\"a\":[{\"s\":2}],\"b\":[{\"s\":1}]}", "0,0", ab);
        noSig(held(h), q);
    }

    @Test
    void g5_unitRecordsAckVsNone_noSig() {
        String none = "{\"c0\":[{\"s\":{\"t\":\"Put\",\"f\":[0,0,0]}},{\"r\":{\"t\":\"Ack\",\"f\":[]}}],"
                + "\"c1\":[{\"s\":{\"t\":\"Get\",\"f\":[1,0]}},{\"r\":{\"t\":\"None\",\"f\":[]}}]}";
        String ack = none.replace("{\"t\":\"None\",\"f\":[]}", "{\"t\":\"Ack\",\"f\":[]}");
        noSig(held(line(DONE2, none, "0,1,0,0", V2)), line(DONE2, ack, "0,1,0,0", V2));
    }

    @Test
    void g6_nestedRecord() {
        List<String> a = List.of("a");
        Summary v0 = line("{\"a\":\"done\"}", "{\"a\":[{\"s\":{\"t\":\"W\",\"f\":[{\"t\":\"Val\",\"f\":[0]}]}}]}", "",
                a);
        Summary v1 = line("{\"a\":\"done\"}", "{\"a\":[{\"s\":{\"t\":\"W\",\"f\":[{\"t\":\"Val\",\"f\":[1]}]}}]}", "",
                a);
        SetIndex ix = held(v0);
        assertEquals(new SetIndex.Covers.NoSig(), ix.covers(v1));
        found(ix.covers(v0), 0, true);
        counters(ix, 1, 0, 0);
    }

    @Test
    void g7_setSortedByEncodedBytes_listsCompareInOrder() {
        List<String> a = List.of("a");
        Summary canonical = line("{\"a\":\"done\"}", "{\"a\":[{\"s\":[10,9]}]}", "", a);
        Summary other = line("{\"a\":\"done\"}", "{\"a\":[{\"s\":[9,10]}]}", "", a);
        SetIndex ix = held(canonical);
        found(ix.covers(line("{\"a\":\"done\"}", "{\"a\":[{\"s\":[10,9]}]}", "", a)), 0, true);
        assertEquals(new SetIndex.Covers.NoSig(), ix.covers(other));
    }

    // ------------------------------------------------------------------ §1c insertion

    @Test
    void i1_duplicateRefused_seqNotAdvanced() {
        SetIndex ix = new SetIndex();
        assertEquals(new SetIndex.Inserted.Stored(0), ix.insert(inW(A)));
        assertEquals(new SetIndex.Inserted.Duplicate(), ix.insert(inW(A)));
        assertEquals(new SetIndex.Inserted.Stored(1), ix.insert(inW(Q)));
        SetIndex.Stats s = ix.stats();
        assertEquals(List.of(2L, 2L, 1L), List.of((long) s.entries(), s.admitted(), s.duplicates()));
    }

    @Test
    void i2_sameCountsTwoSignatures_sigIdAndSeqOrders() {
        SetIndex ix = new SetIndex();
        assertEquals(new SetIndex.Inserted.Stored(0), ix.insert(inW(P)));
        assertEquals(new SetIndex.Inserted.Stored(1), ix.insert(wp(A)));
        assertEquals(new SetIndex.Inserted.Stored(2), ix.insert(inW(A)));
        assertEquals(new SetIndex.Inserted.Stored(3), ix.insert(wp(Q)));
        assertEquals(new SetIndex.Inserted.Duplicate(), ix.insert(inW(P)));
        List<String> got = new ArrayList<>();
        for (SetIndex.Entry e : ix.entries()) {
            got.add(e.sigId() + "/" + e.seq() + "/" + e.counts());
        }
        assertEquals(List.of("0/0/[0, 3, 3, 0, 0, 0]", "0/2/[0, 0, 0, 0, 3, 3]", "1/1/[0, 0, 0, 0, 3, 3]",
                "1/3/[0, 0, 1, 0, 3, 3]"), got);
        assertEquals(2, ix.stats().signatures());
        assertEquals(4, ix.stats().entries());
        assertEquals(2, ix.stats().slotMax());
    }

    @Test
    void i3_insertionOrderChangesWitnessNotClass() {
        SetIndex zpa = held(inW(Z), inW(P), inW(A));
        SetIndex paz = held(inW(P), inW(A), inW(Z));
        found(zpa.covers(inW(Q)), 0, false);
        found(paz.covers(inW(Q)), 1, false);
        assertEquals(List.of(0, 0, 0, 0, 0, 0), witness(zpa, 0));
        assertEquals(List.of(0, 0, 0, 0, 3, 3), witness(paz, 1));
        counters(zpa, 0, 1, 6);
        counters(paz, 0, 2, 8);
        found(zpa.covers(inW(A)), 2, true);
        found(paz.covers(inW(A)), 1, true);
        assertEquals(zpa.stats().exactHits(), paz.stats().exactHits());
        // NotFound and NoSig are order-independent too (N against {P, A} in both orders: n1/n2).
        SetIndex pa = held(inW(P), inW(A));
        SetIndex ap = held(inW(A), inW(P));
        assertEquals(pa.covers(inW(N)), ap.covers(inW(N)));
        assertEquals(pa.covers(wp(A)), ap.covers(wp(A)));
    }
}
