package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T1;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T2;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T3;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T4;
import static org.mpi_sws.jmc.mp.GraphTestSupport.block;
import static org.mpi_sws.jmc.mp.GraphTestSupport.key;
import static org.mpi_sws.jmc.mp.GraphTestSupport.nondet;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Must <b>Definition 3.8</b>: a graph is consistent iff it is well-formed and
 * {@code consistent_M} holds for every model {@code M} it uses, each checked over that model's
 * events only. This is what lets one program mix models (Must §1.3; plan/state/state.md §5.2,
 * "Mixed models") without the models constraining one another — while {@code porf}, which they
 * <em>do</em> share, still crosses between them (that half is {@code MpConsistencyCdTest}).
 *
 * <p>{@link MpConsistency#check} short-circuits: well-formedness first, then the models in enum
 * order, returning the first violation it finds. The third test pins that order, because the
 * clause name is what tells a failing enumerator self-check <em>which</em> definition the
 * operational rule broke.
 */
public class MpConsistencyMixedModelTest {

    /**
     * Both directions of "per model, over that model's events": a p2p violation is found while cd
     * traffic in the same graph is consistent, and a cd violation is found while p2p traffic in the
     * same graph is consistent. Neither model's events can rescue or condemn the other's.
     */
    @Test
    void check_isPerModelOverThatModelsEvents() {
        // (1) T1 sends twice to T2 under p2p and T2 reads them out of order — Def. 3.5 (c).
        //     Beside it, T3 sends once to T4 under cd and T4 reads it — perfectly consistent.
        ExecutionGraph p2pBroken =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(recv(T2, 1, CommunicationModel.P2P))
                        .add(send(T3, 0, CommunicationModel.CD, T4, new Msg(3)))
                        .add(recv(T4, 0, CommunicationModel.CD))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .readsFrom(key(T2, 1), key(T1, 0))
                        .readsFrom(key(T4, 0), key(T3, 0))
                        .build();

        assertEquals(
                EnumSet.of(CommunicationModel.P2P, CommunicationModel.CD),
                MpConsistency.modelsUsed(p2pBroken));
        assertEquals(Optional.empty(), MpConsistency.wellFormedness(p2pBroken));
        assertEquals("Def. 3.5 (c)", MpConsistency.check(p2pBroken).orElseThrow().clause());
        assertEquals(
                Optional.empty(),
                MpConsistency.checkModel(p2pBroken, CommunicationModel.CD),
                "the cd events are consistent, and the p2p breakage does not reach them");

        // (2) The mirror image: T1's two cd sends to T3 are porf-ordered and read out of order —
        //     Def. 3.6 (c) — beside a single, unimpeachable p2p exchange.
        ExecutionGraph cdBroken =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.CD, T3, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.CD, T3, new Msg(2)))
                        .add(recv(T3, 0, CommunicationModel.CD))
                        .add(recv(T3, 1, CommunicationModel.CD))
                        .add(send(T2, 0, CommunicationModel.P2P, T4, new Msg(5)))
                        .add(recv(T4, 0, CommunicationModel.P2P))
                        .readsFrom(key(T3, 0), key(T1, 1))
                        .readsFrom(key(T3, 1), key(T1, 0))
                        .readsFrom(key(T4, 0), key(T2, 0))
                        .build();

        assertEquals(Optional.empty(), MpConsistency.wellFormedness(cdBroken));
        assertEquals("Def. 3.6 (c)", MpConsistency.check(cdBroken).orElseThrow().clause());
        assertEquals(
                Optional.empty(),
                MpConsistency.checkModel(cdBroken, CommunicationModel.P2P),
                "the p2p events are consistent, and the cd breakage does not reach them");
        assertEquals(
                Optional.empty(),
                MpConsistency.checkModel(cdBroken, CommunicationModel.ASYN),
                "Def. 3.4 never rejects a well-formed graph, whatever else is in it");
    }

    /** {@code modelsUsed} reads the labels of sends and receives, and nothing else. */
    @Test
    void modelsUsed_reportsSendsAndReceives() {
        ExecutionGraph none =
                new ExecutionGraph.Builder()
                        .add(nondet(T1, 0, 2, 1))
                        .add(block(T1, 1))
                        .build();
        assertEquals(EnumSet.noneOf(CommunicationModel.class), MpConsistency.modelsUsed(none));
        assertEquals(Optional.empty(), MpConsistency.check(none), "and such a graph is consistent");

        // A send under one model and a receive under another: modelsUsed reports both. It is a
        // property of the labels, so it does not depend on the graph being well-formed — and this
        // graph is not (the receive reads neither a send nor ⊥).
        ExecutionGraph fromLabels =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.MBOX, true, ANY))
                        .build();
        assertEquals(
                EnumSet.of(CommunicationModel.ASYN, CommunicationModel.MBOX),
                MpConsistency.modelsUsed(fromLabels));
        assertTrue(MpConsistency.wellFormedness(fromLabels).isPresent());

        ExecutionGraph all =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(send(T1, 2, CommunicationModel.CD, T2, new Msg(3)))
                        .add(send(T1, 3, CommunicationModel.MBOX, T2, new Msg(4)))
                        .add(nondet(T2, 0, 3, 0))
                        .build();
        assertEquals(EnumSet.allOf(CommunicationModel.class), MpConsistency.modelsUsed(all));
    }

    /** Well-formedness is checked first, then the models in enum order; the first hit is returned. */
    @Test
    void check_returnsTheFirstViolationOnly_wellFormednessFirst() {
        // T2's two p2p receives are out of order (Def. 3.5 (c)) AND T3's blocking receive reads ⊥.
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(recv(T2, 1, CommunicationModel.P2P))
                        .add(recv(T3, 0, CommunicationModel.P2P, true, ANY))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .readsFrom(key(T2, 1), key(T1, 0))
                        .readsBottom(key(T3, 0));
        ExecutionGraph both = b.build();

        assertEquals(
                "Def. 3.5 (c)",
                MpConsistency.checkModel(both, CommunicationModel.P2P).orElseThrow().clause(),
                "the model violation really is there …");
        assertEquals(
                "Def. 3.3 (only non-blocking receives read ⊥)",
                MpConsistency.check(both).orElseThrow().clause(),
                "… and check() still reports the well-formedness one");

        // Repair only the well-formedness clause and the model violation surfaces.
        ExecutionGraph wellFormed =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(recv(T2, 1, CommunicationModel.P2P))
                        .add(recv(T3, 0, CommunicationModel.P2P, false, ANY))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .readsFrom(key(T2, 1), key(T1, 0))
                        .readsBottom(key(T3, 0))
                        .build();
        assertEquals("Def. 3.5 (c)", MpConsistency.check(wellFormed).orElseThrow().clause());

        // Two model violations in one graph: the enum order (ASYN, P2P, CD, MBOX) decides, so p2p
        // is reported and the cd one is not — "the first violation", not "the violations".
        ExecutionGraph twoModels =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(recv(T2, 1, CommunicationModel.P2P))
                        .add(send(T3, 0, CommunicationModel.CD, T4, new Msg(3)))
                        .add(send(T3, 1, CommunicationModel.CD, T4, new Msg(4)))
                        .add(recv(T4, 0, CommunicationModel.CD))
                        .add(recv(T4, 1, CommunicationModel.CD))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .readsFrom(key(T2, 1), key(T1, 0))
                        .readsFrom(key(T4, 0), key(T3, 1))
                        .readsFrom(key(T4, 1), key(T3, 0))
                        .build();
        assertEquals("Def. 3.5 (c)", MpConsistency.check(twoModels).orElseThrow().clause());
        assertEquals(
                "Def. 3.6 (c)",
                MpConsistency.checkModel(twoModels, CommunicationModel.CD).orElseThrow().clause(),
                "the cd violation is present and simply not the one reported");
    }
}
