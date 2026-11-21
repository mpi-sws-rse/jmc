package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T1;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T2;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T3;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T4;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T5;
import static org.mpi_sws.jmc.mp.GraphTestSupport.key;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;

import java.util.List;
import java.util.Optional;

/**
 * Must <b>Definition 3.7</b> ({@code consistent_mbox}) under the reading plan/state/state.md §5.2
 * fixes: the witness {@code so} is a total order on the {@code mbox} sends that <b>contains
 * {@code porf}</b>, not merely one consistent with {@code po}. That correction is review round 2's
 * finding <b>M2</b>; the graph it was found with is encoded here as
 * {@code round2M2WitnessGraph_isRejected}, together with the {@code po}-consistent witness the old
 * reading would have accepted it by.
 *
 * <p>{@link MpConsistency} does not search for a total order. It checks that
 * {@code porf|mbox ∪ forced} is acyclic, which is equivalent (state.md §5.2, round 3, n3): an
 * acyclic relation extends to a total order satisfying (b) and (c), and every such total order
 * contains the forced pairs, so a cycle in the relation is a cycle in {@code so}. Because
 * {@code so} is one <em>global</em> order, {@code porf} couples destinations —
 * {@code rejects_cycleThroughTwoDestinations} is the smallest graph in which every destination is
 * acyclic on its own and the union is not.
 *
 * <p>The named graphs are the mbox litmus rows of state.md §3.4 / mp-contract.md §6.3:
 * {@code MBOX-2+1} (round 1, B2), {@code P2P→MBOX relay} (round 2, B1) and {@code MBOX-first relay}
 * (round 3, m1). Here they are asserted as <em>consistency verdicts on hand-built graphs</em>; the
 * enumerator's counts of the same programs are S4's job.
 */
// 'mboxFirstRelay_onlyYThenZIsConsistent' has 'YT' — two consecutive capitals where the config
// allows one. The name is fixed by the S2b assignment. Measured: removing this annotation adds
// exactly that one warning.
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class MpConsistencyMboxTest {

    /** {@code MBOX-2+1}: A's handler emits s₁;s₂ to B, C emits s₃ to B, B receives three times. */
    @Test
    void mbox2plus1_allThreeGraphsAreConsistent() {
        assertEquals(Optional.empty(), MpConsistency.check(mbox2plus1(0, 1, 2)), "s₁s₂s₃");
        assertEquals(Optional.empty(), MpConsistency.check(mbox2plus1(0, 2, 1)), "s₁s₃s₂");
        assertEquals(Optional.empty(), MpConsistency.check(mbox2plus1(2, 0, 1)), "s₃s₁s₂");

        // …and exactly those three: the litmus row says 3, and the other three permutations are
        // precisely the ones in which s₂ is received before s₁, i.e. porf ⊄ so.
        assertTrue(MpConsistency.check(mbox2plus1(1, 0, 2)).isPresent(), "s₂s₁s₃");
        assertTrue(MpConsistency.check(mbox2plus1(1, 2, 0)).isPresent(), "s₂s₃s₁");
        assertTrue(MpConsistency.check(mbox2plus1(2, 1, 0)).isPresent(), "s₃s₂s₁");
    }

    /** The same-sender pair is {@code porf}-ordered, so no admissible {@code so} reverses it. */
    @Test
    void mbox2plus1_sameSenderReorderedIsRejected() {
        ExecutionGraph g = mbox2plus1(1, 0, 2);
        assertTrue(g.porfBefore(key(T1, 0), key(T1, 1)), "s₁ is po-, hence porf-, before s₂");

        MpConsistency.Violation v = violation(g);
        assertEquals("Def. 3.7 (no total so ⊇ porf satisfies (b) and (c))", v.clause());
        assertTrue(v.detail().startsWith("forced send order has a cycle: "), v.detail());
    }

    /**
     * Round 2's <b>B1</b> relay: {@code A: send^p2p(D,x); send^mbox(B,y)}, {@code D on x:
     * send^mbox(B,z)}, B receives twice. {@code y} and {@code z} are {@code porf}-unordered — the
     * path runs A:0 → D, not A:1 → D — so both receipt orders are mbox-consistent.
     */
    @Test
    void p2pToMboxRelay_bothGraphsAreConsistent() {
        ExecutionGraph yz = p2pToMboxRelay(true);
        ExecutionGraph zy = p2pToMboxRelay(false);

        assertFalse(yz.porfBefore(key(T1, 1), key(T4, 1)), "y is not porf-before z");
        assertFalse(yz.porfBefore(key(T4, 1), key(T1, 1)), "nor z before y");
        assertEquals(Optional.empty(), MpConsistency.check(yz), "y, z");
        assertEquals(Optional.empty(), MpConsistency.check(zy), "z, y");
    }

    /**
     * Round 3's <b>m1</b> relay: {@code A: send^mbox(B,y); send^p2p(D,x)}, {@code D on x:
     * send^mbox(B,z)}. Now {@code y} <em>is</em> {@code porf}-before {@code z} (through A's own
     * program order into the p2p message), so only one order stands.
     */
    @Test
    void mboxFirstRelay_onlyYThenZIsConsistent() {
        ExecutionGraph yz = mboxFirstRelay(true);
        ExecutionGraph zy = mboxFirstRelay(false);

        assertTrue(yz.porfBefore(key(T1, 0), key(T4, 1)), "y porf z: A:0 →po A:1 →rf D:0 →po D:1");
        assertEquals(Optional.empty(), MpConsistency.check(yz), "y, z");
        assertEquals(
                "Def. 3.7 (no total so ⊇ porf satisfies (b) and (c))", violation(zy).clause(), "z, y");
    }

    /**
     * Review round 2, <b>M2</b>: {@code A: s₁ = mbox→B}; {@code B on s₁: y = p2p→C};
     * {@code C on y: s₂ = mbox→D}; {@code E: e₁ = mbox→D; e₂ = mbox→B}; D reads {@code s₂} before
     * {@code e₁} and B reads {@code e₂} before {@code s₁}.
     *
     * <p>No run of the LTS produces this graph, and the current predicate rejects it. The second
     * half of the test is the reason the finding was a finding: it exhibits the reviewer's witness
     * {@code s₂ < e₁ < e₂ < s₁}, checks mechanically that it is {@code po}-consistent and satisfies
     * (b) and (c) — so the superseded {@code po}-only reading of Definition 3.7 would have
     * <em>accepted</em> this graph — and then checks that it is not {@code porf}-consistent, which
     * is what the corrected reading demands.
     */
    @Test
    void round2M2WitnessGraph_isRejected() {
        ExecutionGraph g = round2M2Witness();
        EventKey s1 = key(T1, 0);
        EventKey s2 = key(T3, 1);
        EventKey e1 = key(T5, 0);
        EventKey e2 = key(T5, 1);

        assertEquals(Optional.empty(), MpConsistency.wellFormedness(g), "the graph is well-formed");
        assertEquals("Def. 3.7 (no total so ⊇ porf satisfies (b) and (c))", violation(g).clause());

        // The witness the po-only reading would have used, in the reviewer's order.
        List<EventKey> witness = List.of(s2, e1, e2, s1);

        for (EventKey a : witness) {
            for (EventKey b : witness) {
                if (g.poBefore(a, b)) {
                    assertTrue(
                            witness.indexOf(a) < witness.indexOf(b),
                            "the witness is po-consistent: " + a + " before " + b);
                }
            }
        }
        for (MpConsistency.Forced f : MpConsistency.forcedPairs(g, CommunicationModel.MBOX)) {
            assertFalse(
                    witness.indexOf(f.other()) < witness.indexOf(f.s()),
                    "the witness satisfies (b) and (c): " + f.other() + " is not before " + f.s());
        }

        // …and it is not porf-consistent, which is the whole of M2.
        assertTrue(g.porfBefore(s1, s2), "s₁ is porf-before s₂, through B's receive and C's relay");
        assertTrue(
                witness.indexOf(s1) > witness.indexOf(s2),
                "yet the witness puts s₂ first — so the po-only reading admits a graph no run makes");
    }

    /**
     * Discriminating construction for this class, and the reason the check is one global relation
     * rather than one per destination: two destinations, each of whose forced relations is acyclic
     * on its own, joined into a cycle by two {@code po} edges.
     *
     * <pre>
     *   T1: y = mbox→T3 ; p = mbox→T4      (so y porf p)
     *   T2: q = mbox→T4 ; x = mbox→T3      (so q porf x)
     *   T3: reads x, then y                (so x forced before y)
     *   T4: reads p, then q                (so p forced before q)
     *   ⇒ x → y → p → q → x
     * </pre>
     */
    @Test
    void rejects_cycleThroughTwoDestinations() {
        ExecutionGraph g = crossDestination(false);
        assertEquals(Optional.empty(), MpConsistency.wellFormedness(g));
        assertEquals("Def. 3.7 (no total so ⊇ porf satisfies (b) and (c))", violation(g).clause());

        // Neither destination is inconsistent on its own: no porf edge relates the two sends to T3,
        // and none relates the two sends to T4, so a per-destination check would find nothing.
        assertFalse(g.porfBefore(key(T2, 1), key(T1, 0)), "x and y are porf-unordered");
        assertFalse(g.porfBefore(key(T1, 0), key(T2, 1)));
        assertFalse(g.porfBefore(key(T1, 1), key(T2, 0)), "p and q are porf-unordered");
        assertFalse(g.porfBefore(key(T2, 0), key(T1, 1)));

        // The accepted counterpart: reverse T3's receipt order and the cycle opens.
        assertEquals(Optional.empty(), MpConsistency.check(crossDestination(true)));
    }

    /** The violation names the cycle it found, starting and ending at the same send. */
    @Test
    void violation_namesTheCycle() {
        MpConsistency.Violation v = violation(crossDestination(false));
        String detail = v.detail();

        // The DFS starts at the first mbox send in insertion order, which is y = T1:0.
        assertTrue(
                detail.startsWith("forced send order has a cycle: [" + key(T1, 0)),
                detail);
        assertTrue(detail.endsWith(key(T1, 0) + "]"), "the named path closes on itself: " + detail);
        for (EventKey k : List.of(key(T1, 0), key(T1, 1), key(T2, 0), key(T2, 1))) {
            assertTrue(detail.contains(k.toString()), k + " missing from " + detail);
        }
        assertEquals(v.clause() + ": " + detail, v.toString());
    }

    // ---- constructions ----

    /**
     * {@code MBOX-2+1}: T1 sends s₁ then s₂ to T2, T3 sends s₃ to T2, T2 receives three times in
     * the given order.
     *
     * @param order the send read by T2's first, second and third receive, as 0 = s₁, 1 = s₂, 2 = s₃
     */
    private static ExecutionGraph mbox2plus1(int... order) {
        List<EventKey> sends = List.of(key(T1, 0), key(T1, 1), key(T3, 0));
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.MBOX, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.MBOX, T2, new Msg(2)))
                        .add(send(T3, 0, CommunicationModel.MBOX, T2, new Msg(3)))
                        .add(recv(T2, 0, CommunicationModel.MBOX))
                        .add(recv(T2, 1, CommunicationModel.MBOX))
                        .add(recv(T2, 2, CommunicationModel.MBOX));
        for (int i = 0; i < order.length; i++) {
            b.readsFrom(key(T2, i), sends.get(order[i]));
        }
        return b.build();
    }

    /** {@code A: send^p2p(D,x); send^mbox(B,y)}, {@code D on x: send^mbox(B,z)}. */
    private static ExecutionGraph p2pToMboxRelay(boolean firstReadsY) {
        return relay(
                send(T1, 0, CommunicationModel.P2P, T4, new Msg(10)),
                send(T1, 1, CommunicationModel.MBOX, T2, new Msg(11)),
                key(T1, 0),
                key(T1, 1),
                firstReadsY);
    }

    /** {@code A: send^mbox(B,y); send^p2p(D,x)}, {@code D on x: send^mbox(B,z)}. */
    private static ExecutionGraph mboxFirstRelay(boolean firstReadsY) {
        return relay(
                send(T1, 0, CommunicationModel.MBOX, T2, new Msg(11)),
                send(T1, 1, CommunicationModel.P2P, T4, new Msg(10)),
                key(T1, 1),
                key(T1, 0),
                firstReadsY);
    }

    /**
     * The two relay shapes differ only in the order of A's two sends, so they share one builder:
     * A emits {@code first} then {@code second}, D reads the p2p one and emits {@code z} to B, and
     * B receives twice.
     */
    private static ExecutionGraph relay(
            GraphEvent first,
            GraphEvent second,
            EventKey x,
            EventKey y,
            boolean firstReadsY) {
        EventKey z = key(T4, 1);
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(first)
                        .add(second)
                        .add(recv(T4, 0, CommunicationModel.P2P))
                        .add(send(T4, 1, CommunicationModel.MBOX, T2, new Msg(12)))
                        .add(recv(T2, 0, CommunicationModel.MBOX))
                        .add(recv(T2, 1, CommunicationModel.MBOX))
                        .readsFrom(key(T4, 0), x);
        if (firstReadsY) {
            b.readsFrom(key(T2, 0), y).readsFrom(key(T2, 1), z);
        } else {
            b.readsFrom(key(T2, 0), z).readsFrom(key(T2, 1), y);
        }
        return b.build();
    }

    /** The round-2 M2 witness graph, exactly as the verdict states it. */
    private static ExecutionGraph round2M2Witness() {
        return new ExecutionGraph.Builder()
                .add(send(T1, 0, CommunicationModel.MBOX, T2, new Msg(1))) // A: s₁ → B
                .add(recv(T2, 0, CommunicationModel.MBOX)) // B: first receive
                .add(recv(T2, 1, CommunicationModel.MBOX)) // B: second receive
                .add(send(T2, 2, CommunicationModel.P2P, T3, new Msg(2))) // B on s₁: y → C
                .add(recv(T3, 0, CommunicationModel.P2P)) // C: receives y
                .add(send(T3, 1, CommunicationModel.MBOX, T4, new Msg(3))) // C on y: s₂ → D
                .add(recv(T4, 0, CommunicationModel.MBOX)) // D: first receive
                .add(recv(T4, 1, CommunicationModel.MBOX)) // D: second receive
                .add(send(T5, 0, CommunicationModel.MBOX, T4, new Msg(4))) // E: e₁ → D
                .add(send(T5, 1, CommunicationModel.MBOX, T2, new Msg(5))) // E: e₂ → B
                .readsFrom(key(T3, 0), key(T2, 2))
                .readsFrom(key(T2, 0), key(T5, 1)) // B reads e₂ …
                .readsFrom(key(T2, 1), key(T1, 0)) // … before s₁
                .readsFrom(key(T4, 0), key(T3, 1)) // D reads s₂ …
                .readsFrom(key(T4, 1), key(T5, 0)) // … before e₁
                .build();
    }

    /** The smallest cross-destination cycle; {@code reversed} flips T3's receipt order. */
    private static ExecutionGraph crossDestination(boolean reversed) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.MBOX, T3, new Msg(1))) // y
                        .add(send(T1, 1, CommunicationModel.MBOX, T4, new Msg(2))) // p
                        .add(send(T2, 0, CommunicationModel.MBOX, T4, new Msg(3))) // q
                        .add(send(T2, 1, CommunicationModel.MBOX, T3, new Msg(4))) // x
                        .add(recv(T3, 0, CommunicationModel.MBOX))
                        .add(recv(T3, 1, CommunicationModel.MBOX))
                        .add(recv(T4, 0, CommunicationModel.MBOX))
                        .add(recv(T4, 1, CommunicationModel.MBOX))
                        .readsFrom(key(T4, 0), key(T1, 1)) // D reads p …
                        .readsFrom(key(T4, 1), key(T2, 0)); // … before q
        if (reversed) {
            b.readsFrom(key(T3, 0), key(T1, 0)).readsFrom(key(T3, 1), key(T2, 1));
        } else {
            b.readsFrom(key(T3, 0), key(T2, 1)).readsFrom(key(T3, 1), key(T1, 0));
        }
        return b.build();
    }

    private static MpConsistency.Violation violation(ExecutionGraph g) {
        assertEquals(
                Optional.empty(),
                MpConsistency.wellFormedness(g),
                "the construction must be well-formed, so that only Def. 3.7 can reject it");
        Optional<MpConsistency.Violation> v = MpConsistency.checkModel(g, CommunicationModel.MBOX);
        assertTrue(v.isPresent(), "expected a Def. 3.7 violation, got none");
        assertEquals(v, MpConsistency.check(g), "Def. 3.8 must surface the same violation");
        return v.get();
    }
}
