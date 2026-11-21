package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T1;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T2;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T3;
import static org.mpi_sws.jmc.mp.GraphTestSupport.key;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@link MpConsistency#forcedPairs} — the shared half of Definitions 3.5, 3.6 and 3.7. All three
 * have the shape "(b) {@code [US]; so|dst; rf ∩ mval} is empty and (c)
 * {@code po; rf⁻¹; (so|dst; rf ∩ mval)} is irreflexive" and differ only in {@code so}, so the
 * predicate computes the pairs {@code (s, s')} both clauses talk about once: for a receive
 * {@code r} reading {@code s}, every send {@code s'} of the same model, to the same destination,
 * matching {@code r}'s predicate, that was still available at {@code r} — unread (clause (b)) or
 * read by a {@code po}-later receive (clause (c)).
 *
 * <p>These two tests pin what goes into that set and what stays out. They are the tests that would
 * catch an {@code so}-independent defect — a missing {@code ∩ mval}, a missing destination filter,
 * a {@code ⊥} read treated as a constraint — in one place rather than three times over.
 */
public class MpConsistencyForcedPairsTest {

    /** Both clauses, in one graph, with the exact list and its documented order. */
    @Test
    void forcedPairs_enumeratesExactlyTheUnreadAndReadLaterMatchingSends() {
        // T1 sends 1, 2, 3 to T2; T2 reads the second, then the first; the third is never read.
        ExecutionGraph g =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(send(T1, 2, CommunicationModel.P2P, T2, new Msg(3)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(recv(T2, 1, CommunicationModel.P2P))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .readsFrom(key(T2, 1), key(T1, 0))
                        .build();

        assertEquals(
                List.of(
                        // r = T2:0 reads T1:1 — T1:0 is read by the po-later T2:1 …
                        new MpConsistency.Forced(key(T1, 1), key(T1, 0), key(T2, 0), false),
                        // … and T1:2 is unread.
                        new MpConsistency.Forced(key(T1, 1), key(T1, 2), key(T2, 0), true),
                        // r = T2:1 reads T1:0 — T1:1 was read EARLIER, so only T1:2 is forced.
                        new MpConsistency.Forced(key(T1, 0), key(T1, 2), key(T2, 1), true)),
                MpConsistency.forcedPairs(g, CommunicationModel.P2P),
                "in insertion order of r, then of s'");

        // The pairs are an enumeration, not a verdict: it is so that decides. Here so = po does
        // contradict the first pair, so this particular graph is p2p-inconsistent.
        assertEquals("Def. 3.5 (c)", MpConsistency.check(g).orElseThrow().clause());
    }

    /**
     * The four exclusions, each present in the graph and each absent from the answer: a send whose
     * value is outside {@code vals(r)}, a send read by a {@code po}-<em>earlier</em> receive, a
     * receive that read {@code ⊥}, and sends to another destination or under another model.
     */
    @Test
    void forcedPairs_ignoresNonMatching_readEarlier_bottomReads_andOtherDestinations() {
        Predicate<MpMessage> atMostTwo = m -> m instanceof Msg msg && msg.n() <= 2;
        ExecutionGraph g =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(send(T1, 2, CommunicationModel.P2P, T3, new Msg(9))) // other destination
                        .add(send(T1, 3, CommunicationModel.MBOX, T2, new Msg(8))) // other model
                        .add(send(T1, 4, CommunicationModel.P2P, T2, new Msg(7))) // outside vals(T2:0)
                        .add(recv(T2, 0, CommunicationModel.P2P, true, atMostTwo))
                        .add(recv(T2, 1, CommunicationModel.P2P, true, ANY))
                        .add(recv(T2, 2, CommunicationModel.P2P, false, ANY)) // reads ⊥
                        .add(recv(T2, 3, CommunicationModel.MBOX, true, ANY))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .readsFrom(key(T2, 1), key(T1, 1))
                        .readsBottom(key(T2, 2))
                        .readsFrom(key(T2, 3), key(T1, 3))
                        .build();

        assertEquals(Optional.empty(), MpConsistency.wellFormedness(g));
        assertEquals(
                List.of(
                        // r = T2:0 reads T1:0: T1:1 is matching and read po-later. T1:2 is to T3,
                        // T1:3 is mbox, T1:4 carries 7 and is outside vals(T2:0).
                        new MpConsistency.Forced(key(T1, 0), key(T1, 1), key(T2, 0), false),
                        // r = T2:1 reads T1:1: T1:0 was read EARLIER, T1:4 is unread and matches ANY.
                        new MpConsistency.Forced(key(T1, 1), key(T1, 4), key(T2, 1), true)),
                MpConsistency.forcedPairs(g, CommunicationModel.P2P),
                "T2:2 read ⊥ and contributes nothing at all");

        // The mbox receive sees only the mbox send it read, so nothing is forced under mbox either.
        assertEquals(List.of(), MpConsistency.forcedPairs(g, CommunicationModel.MBOX));
        assertEquals(List.of(), MpConsistency.forcedPairs(g, CommunicationModel.CD), "no cd events");

        // Discriminating half: widen T2:0's predicate to accept 7 and the excluded pair appears —
        // so the exclusion above is ∩ mval doing its job, not the enumeration missing a send.
        ExecutionGraph widened = widen(g);
        assertEquals(
                3,
                MpConsistency.forcedPairs(widened, CommunicationModel.P2P).size(),
                "with vals(T2:0) widened, T1:4 is forced from T2:0 as well");
        assertTrue(
                MpConsistency.forcedPairs(widened, CommunicationModel.P2P)
                        .contains(
                                new MpConsistency.Forced(key(T1, 0), key(T1, 4), key(T2, 0), true)));

        // Both graphs are p2p-consistent: every forced pair points the way so already points.
        assertEquals(Optional.empty(), MpConsistency.check(g));
        assertEquals(Optional.empty(), MpConsistency.check(widened));
    }

    /** The same graph with {@code T2:0}'s predicate replaced by one that accepts everything. */
    private static ExecutionGraph widen(ExecutionGraph g) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(send(T1, 2, CommunicationModel.P2P, T3, new Msg(9)))
                        .add(send(T1, 3, CommunicationModel.MBOX, T2, new Msg(8)))
                        .add(send(T1, 4, CommunicationModel.P2P, T2, new Msg(7)))
                        .add(recv(T2, 0, CommunicationModel.P2P, true, ANY))
                        .add(recv(T2, 1, CommunicationModel.P2P, true, ANY))
                        .add(recv(T2, 2, CommunicationModel.P2P, false, ANY))
                        .add(recv(T2, 3, CommunicationModel.MBOX, true, ANY))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .readsFrom(key(T2, 1), key(T1, 1))
                        .readsBottom(key(T2, 2))
                        .readsFrom(key(T2, 3), key(T1, 3));
        assertEquals(g.size(), b.build().size(), "the widened graph differs only in a predicate");
        return b.build();
    }
}
