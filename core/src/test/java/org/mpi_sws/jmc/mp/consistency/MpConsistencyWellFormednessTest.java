package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T1;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T2;
import static org.mpi_sws.jmc.mp.GraphTestSupport.T3;
import static org.mpi_sws.jmc.mp.GraphTestSupport.block;
import static org.mpi_sws.jmc.mp.GraphTestSupport.key;
import static org.mpi_sws.jmc.mp.GraphTestSupport.nondet;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;
import static org.mpi_sws.jmc.mp.MpTestSupport.msgIs;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Must <b>Definition 3.3</b> (well-formedness), one test per clause, each carrying the accepted
 * counterpart of the graph it rejects — the graph that differs in exactly the one thing the clause
 * is about.
 *
 * <p>The five clauses, in the paper's order, and the {@link MpConsistency.Violation#clause()} each
 * produces:
 *
 * <ul>
 *   <li>{@code rf} is total on receives — {@code "Def. 3.2(3) (rf total)"} (the Java encoding splits
 *       Must's "reads a single value" into this and the functionality of the recorded map, which
 *       {@link ExecutionGraph.Builder} enforces structurally);</li>
 *   <li>only non-blocking receives read {@code ⊥} —
 *       {@code "Def. 3.3 (only non-blocking receives read ⊥)"};</li>
 *   <li>sends are read at most once — {@code "Def. 3.3 (sends are read at most once)"};</li>
 *   <li>{@code rf} relates matching destination, model and value —
 *       {@code "Def. 3.3 (rf destination)"} / {@code "(rf model)"} / {@code "(rf value ∈ vals)"},
 *       the last being {@code mval};</li>
 *   <li>no causal cycles — {@code "Def. 3.3 (no causal cycles)"}.</li>
 * </ul>
 *
 * <p>Definition 3.4 ({@code consistent_asyn}) is well-formedness and nothing else, so every
 * accepted graph here is also asserted consistent under {@link MpConsistency#check} with
 * {@code ASYN} traffic.
 */
public class MpConsistencyWellFormednessTest {

    @Test
    void rejects_receiveWithNeitherRfNorBottom() {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.ASYN, false, ANY));

        MpConsistency.Violation v = violation(b.build());
        assertEquals("Def. 3.2(3) (rf total)", v.clause());
        assertTrue(v.detail().contains("reads neither a send nor ⊥"), v.detail());

        // Accepted counterpart: the same receive, with an rf recorded — and with ⊥ recorded.
        assertEquals(Optional.empty(), MpConsistency.check(b.readsFrom(key(T2, 0), key(T1, 0)).build()));
        assertEquals(Optional.empty(), MpConsistency.check(b.readsBottom(key(T2, 0)).build()));
    }

    @Test
    void rejects_blockingReceiveReadingBottom_acceptsNonBlocking() {
        ExecutionGraph blocking =
                new ExecutionGraph.Builder()
                        .add(recv(T2, 0, CommunicationModel.ASYN, true, ANY))
                        .readsBottom(key(T2, 0))
                        .build();
        assertEquals(
                "Def. 3.3 (only non-blocking receives read ⊥)", violation(blocking).clause());

        // Accepted counterpart: β = nb, everything else identical.
        ExecutionGraph nonBlocking =
                new ExecutionGraph.Builder()
                        .add(recv(T2, 0, CommunicationModel.ASYN, false, ANY))
                        .readsBottom(key(T2, 0))
                        .build();
        assertEquals(Optional.empty(), MpConsistency.check(nonBlocking));

        // ⊥, unlike a send, may be read by many non-blocking receives.
        ExecutionGraph twice =
                new ExecutionGraph.Builder()
                        .add(recv(T2, 0, CommunicationModel.ASYN, false, ANY))
                        .add(recv(T2, 1, CommunicationModel.ASYN, false, ANY))
                        .readsBottom(key(T2, 0))
                        .readsBottom(key(T2, 1))
                        .build();
        assertEquals(Optional.empty(), MpConsistency.check(twice));
    }

    @Test
    void rejects_sendReadTwice() {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .add(recv(T2, 1, CommunicationModel.ASYN))
                        .readsFrom(key(T2, 0), key(T1, 0));

        MpConsistency.Violation v = violation(b.readsFrom(key(T2, 1), key(T1, 0)).build());
        assertEquals("Def. 3.3 (sends are read at most once)", v.clause());
        assertTrue(v.detail().contains("read by both"), v.detail());

        // Accepted counterpart: the second receive reads the other send.
        assertEquals(Optional.empty(), MpConsistency.check(b.readsFrom(key(T2, 1), key(T1, 1)).build()));
    }

    @Test
    void rejects_rfToWrongDestination() {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T3, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .readsFrom(key(T2, 0), key(T1, 0));

        MpConsistency.Violation v = violation(b.build());
        assertEquals("Def. 3.3 (rf destination)", v.clause());
        assertTrue(v.detail().contains("sent to Tid(3)"), v.detail());

        // Accepted counterpart: the send names the receiving thread.
        ExecutionGraph right =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();
        assertEquals(Optional.empty(), MpConsistency.check(right));
    }

    @Test
    void rejects_rfAcrossModels() {
        ExecutionGraph mixed =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.MBOX))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();

        MpConsistency.Violation v = violation(mixed);
        assertEquals("Def. 3.3 (rf model)", v.clause());
        assertTrue(v.detail().contains("MBOX") && v.detail().contains("P2P"), v.detail());
        assertEquals(
                EnumSet.of(CommunicationModel.P2P, CommunicationModel.MBOX),
                MpConsistency.modelsUsed(mixed),
                "the graph really does use both models — the rf is what is wrong");

        // Accepted counterpart: the same two events under one model.
        ExecutionGraph same =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();
        assertEquals(Optional.empty(), MpConsistency.check(same));
    }

    @Test
    void rejects_rfValueOutsideVals() {
        ExecutionGraph outside =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.ASYN, true, msgIs(1)))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();

        MpConsistency.Violation v = violation(outside);
        assertEquals("Def. 3.3 (rf value ∈ vals)", v.clause());
        assertTrue(v.detail().contains("does not accept"), v.detail());

        // Accepted counterpart: the same graph with a predicate that accepts the value — mval.
        ExecutionGraph inside =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.ASYN, true, msgIs(2)))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();
        assertEquals(Optional.empty(), MpConsistency.check(inside));
    }

    @Test
    void rejects_causalCycle() {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(recv(T1, 0, CommunicationModel.ASYN))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .add(send(T2, 1, CommunicationModel.ASYN, T1, new Msg(1)))
                        .readsFrom(key(T2, 0), key(T1, 1));

        // Each receive reads a send that is po-after the other receive: rf ; po ; rf ; po is a cycle.
        MpConsistency.Violation v = violation(b.readsFrom(key(T1, 0), key(T2, 1)).build());
        assertEquals("Def. 3.3 (no causal cycles)", v.clause());
        assertEquals("porf is not irreflexive", v.detail());

        // Accepted counterpart: the same graph with T1's receive reading ⊥ instead — one edge less.
        ExecutionGraph acyclic =
                new ExecutionGraph.Builder()
                        .add(recv(T1, 0, CommunicationModel.ASYN, false, ANY))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .add(send(T2, 1, CommunicationModel.ASYN, T1, new Msg(1)))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .readsBottom(key(T1, 0))
                        .build();
        assertEquals(Optional.empty(), MpConsistency.check(acyclic));
    }

    /**
     * The positive direction, over a graph containing one of every label: sends, a blocking receive
     * reading a send, a non-blocking receive reading {@code ⊥}, a nondeterministic choice, a block
     * event, an unread send, and spawn/join edges. Under {@code asyn} (Definition 3.4) consistency
     * <em>is</em> well-formedness, so {@link MpConsistency#check} must also be empty.
     */
    @Test
    void accepts_wellFormedGraph_andReportsNoViolation() {
        ExecutionGraph g =
                new ExecutionGraph.Builder()
                        .add(nondet(T1, 0, 3, 2))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(send(T1, 2, CommunicationModel.ASYN, T2, new Msg(7)))
                        .add(block(T1, 3))
                        .add(recv(T2, 0, CommunicationModel.ASYN, true, msgIs(1)))
                        .add(recv(T2, 1, CommunicationModel.ASYN, false, msgIs(5)))
                        .add(send(T2, 2, CommunicationModel.ASYN, T3, new Msg(3)))
                        .readsFrom(key(T2, 0), key(T1, 1))
                        .readsBottom(key(T2, 1))
                        .hb(key(T1, 0), key(T2, 0))
                        .hb(key(T2, 2), key(T1, 3))
                        .build();

        assertEquals(Optional.empty(), MpConsistency.wellFormedness(g));
        assertEquals(Optional.empty(), MpConsistency.check(g));
        assertEquals(
                Optional.empty(), MpConsistency.checkModel(g, CommunicationModel.ASYN),
                "Def. 3.4: consistent_asyn is well-formedness");
        assertEquals(EnumSet.of(CommunicationModel.ASYN), MpConsistency.modelsUsed(g));
        assertTrue(g.porfAcyclic());
    }

    private static MpConsistency.Violation violation(ExecutionGraph g) {
        Optional<MpConsistency.Violation> v = MpConsistency.wellFormedness(g);
        assertTrue(v.isPresent(), "expected a well-formedness violation, got none");
        assertEquals(v, MpConsistency.check(g), "check() must report the same violation first");
        return v.get();
    }
}
