package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T1;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T2;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T3;
import static org.mpi_sws.jmc.mp.GraphTestSupport.block;
import static org.mpi_sws.jmc.mp.GraphTestSupport.key;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;

import java.util.Optional;

/**
 * Must <b>Definition 3.6</b> ({@code consistent_cd}) — Definition 3.5 with
 * {@code so ≜ [S^cd]; porf; [S^cd]}, still restricted to {@code so|dst}. Two sends to one process
 * that are causally ordered must be received in that order; sends that are causally concurrent may
 * be received in either.
 *
 * <p>What separates cd from p2p is the shape of the causal path, so every test here is about a
 * <em>path</em>: through another thread's receive (Example 3.1), through a message of a
 * <em>different</em> model (Example 2.12 — {@code porf} crosses models, plan/state/state.md §5.2
 * round-1 M1), and through a spawn edge ({@code hb} is part of {@code porf}, L3). Each rejection
 * carries the control in which exactly that path is removed and the same graph is accepted.
 */
// 'example3_1_…' and 'rejects_c_…' fall outside the config's @Test name exemption, and 'ARf'
// and 'ASpawn' are two consecutive capitals where the config allows one. All four names are
// fixed by the S2b assignment. Measured: removing this annotation adds exactly four warnings.
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:AbbreviationAsWordInName"})
public class MpConsistencyCdTest {

    /**
     * Must Example 3.1, last sentence: "If we change the communication model to cd, the graph 2
     * becomes inconsistent: there is a porf path between the two sends to T3, but the messages are
     * received in the opposite order." The path is {@code S(T3,1) —po→ S(T2,2) —rf→ recv —po→
     * S(T3,3)}.
     */
    @Test
    void example3_1_oppositeOrderGraphIsRejectedUnderCd() {
        ExecutionGraph graph1 = example31(true);
        ExecutionGraph graph2 = example31(false);

        assertEquals(Optional.empty(), MpConsistency.check(graph1), "the paper's graph 1 survives cd");

        MpConsistency.Violation v = violation(graph2);
        assertEquals("Def. 3.6 (c)", v.clause());
        assertTrue(v.detail().contains("read by a po-later receive"), v.detail());
        assertTrue(
                graph2.porfBefore(key(T1, 0), key(T2, 1)),
                "S(T3,1) is porf-before S(T3,3), which is exactly what p2p ignores and cd does not");

        // Both graphs are p2p-consistent — the difference is the model, not the graph.
        assertEquals(Optional.empty(), MpConsistency.checkModel(asP2p(false), CommunicationModel.P2P));
    }

    /**
     * Must Example 2.12's shape: the causal path between the two {@code cd} sends runs through a
     * {@code p2p} message. {@code porf} crosses models, so cd's {@code so} does too — the round-1 M1
     * finding, in its declarative form.
     */
    @Test
    void rejects_c_throughARfPathOfAnotherModel() {
        MpConsistency.Violation v = violation(relayThroughP2p(false, true));
        assertEquals("Def. 3.6 (c)", v.clause());

        // In the causal order, accepted.
        assertEquals(Optional.empty(), MpConsistency.check(relayThroughP2p(true, true)));

        // Control: the very same events, with T2's p2p receive reading ⊥ instead of T1's message.
        // The cross-model path is gone, the two cd sends are concurrent, and BOTH orders stand.
        ExecutionGraph noPath = relayThroughP2p(false, false);
        assertFalse(noPath.porfBefore(key(T1, 0), key(T2, 1)), "no rf edge, no causal path");
        assertEquals(Optional.empty(), MpConsistency.check(noPath));
        assertEquals(Optional.empty(), MpConsistency.check(relayThroughP2p(true, false)));
    }

    /** A spawn edge is causal (plan/state/state.md L3), so it orders the child's sends after it. */
    @Test
    void rejects_throughASpawnEdge() {
        MpConsistency.Violation v = violation(spawnedSender(false, true));
        assertEquals("Def. 3.6 (c)", v.clause());
        assertTrue(spawnedSender(false, true).porfBefore(key(T1, 0), key(T2, 0)));

        assertEquals(Optional.empty(), MpConsistency.check(spawnedSender(true, true)), "causal order");

        // Control: the identical graph with the spawn edge removed — the sends are concurrent.
        ExecutionGraph noSpawn = spawnedSender(false, false);
        assertFalse(noSpawn.porfBefore(key(T1, 0), key(T2, 0)));
        assertEquals(Optional.empty(), MpConsistency.check(noSpawn));

        // …and the same through a join edge: T2's send, then T1 joins T2, then T1 sends.
        ExecutionGraph.Builder joined =
                new ExecutionGraph.Builder()
                        .add(send(T2, 0, CommunicationModel.CD, T3, new Msg(2)))
                        .add(block(T1, 0))
                        .add(send(T1, 1, CommunicationModel.CD, T3, new Msg(1)))
                        .add(recv(T3, 0, CommunicationModel.CD))
                        .add(recv(T3, 1, CommunicationModel.CD))
                        .hb(key(T2, 0), key(T1, 1))
                        .readsFrom(key(T3, 0), key(T1, 1))
                        .readsFrom(key(T3, 1), key(T2, 0));
        assertEquals("Def. 3.6 (c)", violation(joined.build()).clause());
        assertEquals(
                Optional.empty(),
                MpConsistency.check(
                        joined.readsFrom(key(T3, 0), key(T2, 0))
                                .readsFrom(key(T3, 1), key(T1, 1))
                                .build()),
                "received in the order the join edge forces");
    }

    /**
     * Discriminating construction for this class in the accepting direction: with no causal path
     * at all between the two sends, cd is as permissive as p2p and both receipt orders stand — so
     * none of the rejections above can be coming from cd rejecting reordering as such.
     */
    @Test
    void accepts_causallyConcurrentSendsInEitherOrder() {
        ExecutionGraph one = concurrentSenders(true);
        ExecutionGraph other = concurrentSenders(false);

        assertEquals(Optional.empty(), MpConsistency.check(one));
        assertEquals(Optional.empty(), MpConsistency.check(other));
        assertFalse(one.porfBefore(key(T1, 0), key(T2, 0)));
        assertFalse(one.porfBefore(key(T2, 0), key(T1, 0)));
        assertEquals(
                1,
                MpConsistency.forcedPairs(other, CommunicationModel.CD).size(),
                "a forced pair exists in each; there is simply no so edge to contradict");
    }

    // ---- constructions ----

    /** Must Example 3.1 under cd; {@code firstReadsT1} selects the paper's graph 1 or graph 2. */
    private static ExecutionGraph example31(boolean firstReadsT1) {
        return example31(firstReadsT1, CommunicationModel.CD);
    }

    /** Must Example 3.1 under any model, so that "the same graph" really is the same graph. */
    private static ExecutionGraph example31(boolean firstReadsT1, CommunicationModel model) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, model, T3, new Msg(1)))
                        .add(send(T1, 1, model, T2, new Msg(2)))
                        .add(recv(T2, 0, model))
                        .add(send(T2, 1, model, T3, new Msg(3)))
                        .add(recv(T3, 0, model))
                        .add(recv(T3, 1, model))
                        .readsFrom(key(T2, 0), key(T1, 1));
        if (firstReadsT1) {
            b.readsFrom(key(T3, 0), key(T1, 0)).readsFrom(key(T3, 1), key(T2, 1));
        } else {
            b.readsFrom(key(T3, 0), key(T2, 1)).readsFrom(key(T3, 1), key(T1, 0));
        }
        return b.build();
    }

    /**
     * {@code T1: send^cd(T3,1); send^p2p(T2,2)}, {@code T2: recv^p2p(); send^cd(T3,3)},
     * {@code T3: recv^cd(); recv^cd()} — Example 3.1 with the middle hop under another model.
     *
     * @param firstReadsT1 whether T3's first receive reads T1's cd send
     * @param relayIsRead whether T2's p2p receive reads T1's p2p send (the causal path) or {@code ⊥}
     */
    private static ExecutionGraph relayThroughP2p(boolean firstReadsT1, boolean relayIsRead) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.CD, T3, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P, false, ANY))
                        .add(send(T2, 1, CommunicationModel.CD, T3, new Msg(3)))
                        .add(recv(T3, 0, CommunicationModel.CD))
                        .add(recv(T3, 1, CommunicationModel.CD));
        if (relayIsRead) {
            b.readsFrom(key(T2, 0), key(T1, 1));
        } else {
            b.readsBottom(key(T2, 0));
        }
        if (firstReadsT1) {
            b.readsFrom(key(T3, 0), key(T1, 0)).readsFrom(key(T3, 1), key(T2, 1));
        } else {
            b.readsFrom(key(T3, 0), key(T2, 1)).readsFrom(key(T3, 1), key(T1, 0));
        }
        return b.build();
    }

    /** The same program under p2p, for the "same graph, different model" assertion. */
    private static ExecutionGraph asP2p(boolean firstReadsT1) {
        return example31(firstReadsT1, CommunicationModel.P2P);
    }

    /** T1 sends to T3 and spawns T2, which also sends to T3. {@code withSpawn} adds the hb edge. */
    private static ExecutionGraph spawnedSender(boolean firstReadsT1, boolean withSpawn) {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.CD, T3, new Msg(1)))
                        .add(send(T2, 0, CommunicationModel.CD, T3, new Msg(2)))
                        .add(recv(T3, 0, CommunicationModel.CD))
                        .add(recv(T3, 1, CommunicationModel.CD));
        if (withSpawn) {
            b.hb(key(T1, 0), key(T2, 0));
        }
        if (firstReadsT1) {
            b.readsFrom(key(T3, 0), key(T1, 0)).readsFrom(key(T3, 1), key(T2, 0));
        } else {
            b.readsFrom(key(T3, 0), key(T2, 0)).readsFrom(key(T3, 1), key(T1, 0));
        }
        return b.build();
    }

    /** The same two senders with no edge between them at all. */
    private static ExecutionGraph concurrentSenders(boolean firstReadsT1) {
        return spawnedSender(firstReadsT1, false);
    }

    private static MpConsistency.Violation violation(ExecutionGraph g) {
        assertEquals(
                Optional.empty(),
                MpConsistency.wellFormedness(g),
                "the construction must be well-formed, so that only Def. 3.6 can reject it");
        Optional<MpConsistency.Violation> v = MpConsistency.checkModel(g, CommunicationModel.CD);
        assertTrue(v.isPresent(), "expected a Def. 3.6 violation, got none");
        assertEquals(v, MpConsistency.check(g), "Def. 3.8 must surface the same violation");
        return v.get();
    }
}
