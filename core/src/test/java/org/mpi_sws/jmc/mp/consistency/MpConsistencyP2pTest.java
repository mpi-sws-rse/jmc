package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T1;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T2;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T3;
import static org.mpi_sws.jmc.mp.GraphTestSupport.key;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;
import static org.mpi_sws.jmc.mp.MpTestSupport.msgIs;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;

import java.util.List;
import java.util.Optional;

/**
 * Must <b>Definition 3.5</b> ({@code consistent_p2p}), whose send order is
 * {@code so ≜ [S^p2p]; po; [S^p2p]} restricted to {@code so|dst}: two messages one thread sends to
 * one destination are received in the order they were sent.
 *
 * <p>Clause (b) — {@code [US]; so|dst; rf ∩ mval} is empty — is the case where the {@code so}-earlier
 * matching send is <b>unread</b>; clause (c) — {@code po; rf⁻¹; (so|dst; rf ∩ mval)} is irreflexive —
 * the case where it is read by a {@code po}-<b>later</b> receive. Each rejection below carries the
 * accepted counterpart that differs in exactly the one thing the clause is about, and each
 * acceptance carries the rejected counterpart, so no assertion here can be passing vacuously.
 *
 * <p>{@code example3_1_bothP2pGraphsAreConsistent} is Must Example 3.1 (Must.pdf §3.2;
 * plan/state/must-algorithm.md §1.3): both of T3's receipt orders are p2p-consistent, because the
 * two sends to T3 come from different threads. {@code MpConsistencyCdTest} shows that the second of
 * them is <em>not</em> cd-consistent — the same graph, the stronger model.
 */
// Three test names here have a digit- or single-letter segment ('rejects_b_…', 'rejects_c_…',
// 'example3_1_…') and so fall outside the SuppressionXpathSingleFilter the config grants @Test
// methods. The names are fixed by the S2b assignment. Measured: removing this annotation adds
// exactly those three warnings and no others.
@SuppressWarnings("checkstyle:MethodName")
public class MpConsistencyP2pTest {

    /** Clause (b): an unread, matching, {@code po}-earlier send to the same destination. */
    @Test
    void rejects_b_unreadSoEarlierMatchingSend() {
        // T1 sends 1 then 2 to T2; T2's only receive reads the second. The first is never read.
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P));

        MpConsistency.Violation v = violation(b.readsFrom(key(T2, 0), key(T1, 1)).build());
        assertEquals("Def. 3.5 (b)", v.clause());
        assertTrue(v.detail().contains("unread"), v.detail());

        // Accepted counterpart: the receive reads the earlier send; the LATER one is then unread,
        // and an so-later unread send constrains nothing.
        assertEquals(Optional.empty(), MpConsistency.check(b.readsFrom(key(T2, 0), key(T1, 0)).build()));
    }

    /** Clause (c): the {@code so}-earlier matching send is read by a {@code po}-later receive. */
    @Test
    void rejects_c_soEarlierMatchingSendReadByPoLaterReceive() {
        MpConsistency.Violation v = violation(twoSendsOneReceiver(false));
        assertEquals("Def. 3.5 (c)", v.clause());
        assertTrue(v.detail().contains("read by a po-later receive"), v.detail());

        // Accepted counterpart: the same four events, received in send order.
        assertEquals(Optional.empty(), MpConsistency.check(twoSendsOneReceiver(true)));
    }

    /**
     * {@code ∩ mval}: an {@code so}-earlier send the receive's predicate does not accept is not a
     * forced pair at all — selective receive as a filter, not as an {@code assume}
     * (plan/state/state.md §5.2, "Selective receive = guards").
     */
    @Test
    void accepts_soEarlierNonMatchingSendUnread() {
        ExecutionGraph selective =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P, true, msgIs(2)))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .build();
        assertEquals(Optional.empty(), MpConsistency.check(selective));
        assertEquals(
                0,
                MpConsistency.forcedPairs(selective, CommunicationModel.P2P).size(),
                "the earlier send is outside vals(r), so it forces nothing");

        // Rejected counterpart: the same graph whose receive accepts everything is clause (b).
        ExecutionGraph unselective =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .build();
        assertEquals("Def. 3.5 (b)", violation(unselective).clause());
    }

    /** The in-order graph: the {@code so}-earlier send is read by a {@code po}-earlier receive. */
    @Test
    void accepts_soEarlierMatchingSendReadByPoEarlierReceive() {
        ExecutionGraph inOrder = twoSendsOneReceiver(true);
        assertEquals(Optional.empty(), MpConsistency.check(inOrder));
        assertEquals(
                1,
                MpConsistency.forcedPairs(inOrder, CommunicationModel.P2P).size(),
                "the first receive still forces s₁ before s₂ — the order it forces is the one so has");

        // Rejected counterpart: swap the two rf edges and the same forced pair now contradicts so.
        assertEquals("Def. 3.5 (c)", violation(twoSendsOneReceiver(false)).clause());
    }

    /** {@code so} is per sender: two threads' sends to one destination are unordered by {@code po}. */
    @Test
    void accepts_sendsFromDifferentSendersInEitherOrder() {
        assertEquals(Optional.empty(), MpConsistency.check(twoSendersOneReceiver(true)));
        assertEquals(Optional.empty(), MpConsistency.check(twoSendersOneReceiver(false)));

        // Rejected counterpart: move both sends into ONE thread and the second order becomes (c).
        assertEquals("Def. 3.5 (c)", violation(twoSendsOneReceiver(false)).clause());
    }

    /**
     * Must Example 3.1: {@code T1: send(T3,1); send(T2,2)}, {@code T2: recv(); send(T3,3)},
     * {@code T3: recv(); recv()} — "along with its p2p-consistent graphs", of which the paper draws
     * two. The two sends to T3 sit in different threads, so {@code so|dst} is empty and neither
     * receipt order is forced.
     */
    @Test
    void example3_1_bothP2pGraphsAreConsistent() {
        ExecutionGraph graph1 = example31(true);
        ExecutionGraph graph2 = example31(false);

        assertEquals(Optional.empty(), MpConsistency.check(graph1), "the paper's graph 1");
        assertEquals(Optional.empty(), MpConsistency.check(graph2), "the paper's graph 2");
        assertTrue(
                graph1.porfBefore(key(T1, 0), key(T2, 1)),
                "S(T3,1) is porf-before S(T3,3) through T2's receive — which p2p does not look at");
        // The forced pair is there in both graphs — what is missing is an so edge contradicting it.
        List<MpConsistency.Forced> forced = MpConsistency.forcedPairs(graph2, CommunicationModel.P2P);
        assertEquals(1, forced.size(), forced.toString());
        assertEquals(key(T2, 1), forced.get(0).s());
        assertEquals(key(T1, 0), forced.get(0).other());
        assertFalse(
                graph2.poBefore(key(T1, 0), key(T2, 1)),
                "so ≜ po does not order two sends of different threads, which is why graph 2 stands");
    }

    // ---- constructions ----

    /** {@code T1: send(T2,1); send(T2,2)}, {@code T2: recv(); recv()}, received in either order. */
    private static ExecutionGraph twoSendsOneReceiver(boolean inOrder) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(recv(T2, 1, CommunicationModel.P2P));
        if (inOrder) {
            b.readsFrom(key(T2, 0), key(T1, 0)).readsFrom(key(T2, 1), key(T1, 1));
        } else {
            b.readsFrom(key(T2, 0), key(T1, 1)).readsFrom(key(T2, 1), key(T1, 0));
        }
        return b.build();
    }

    /** The same shape with the two sends in two threads, so {@code po} orders neither. */
    private static ExecutionGraph twoSendersOneReceiver(boolean firstReadsT1) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T3, new Msg(1)))
                        .add(send(T2, 0, CommunicationModel.P2P, T3, new Msg(2)))
                        .add(recv(T3, 0, CommunicationModel.P2P))
                        .add(recv(T3, 1, CommunicationModel.P2P));
        if (firstReadsT1) {
            b.readsFrom(key(T3, 0), key(T1, 0)).readsFrom(key(T3, 1), key(T2, 0));
        } else {
            b.readsFrom(key(T3, 0), key(T2, 0)).readsFrom(key(T3, 1), key(T1, 0));
        }
        return b.build();
    }

    /** Must Example 3.1 under p2p; {@code firstReadsT1} selects the paper's graph 1 or graph 2. */
    private static ExecutionGraph example31(boolean firstReadsT1) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T3, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(send(T2, 1, CommunicationModel.P2P, T3, new Msg(3)))
                        .add(recv(T3, 0, CommunicationModel.P2P))
                        .add(recv(T3, 1, CommunicationModel.P2P))
                        .readsFrom(key(T2, 0), key(T1, 1));
        if (firstReadsT1) {
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
                "the construction must be well-formed, so that only Def. 3.5 can reject it");
        Optional<MpConsistency.Violation> v = MpConsistency.checkModel(g, CommunicationModel.P2P);
        assertTrue(v.isPresent(), "expected a Def. 3.5 violation, got none");
        assertEquals(v, MpConsistency.check(g), "Def. 3.8 must surface the same violation");
        return v.get();
    }
}
