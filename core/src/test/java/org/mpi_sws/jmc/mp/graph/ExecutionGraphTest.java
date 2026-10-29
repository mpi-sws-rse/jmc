package org.mpi_sws.jmc.mp.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mpi_sws.jmc.mp.events.EventKey;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pins {@link ExecutionGraph}, its {@link ExecutionGraph.Builder} and {@link GraphCanon} — the
 * carrier of Must Definition 3.2 ({@code G = ⟨E, po, rf⟩}), of the insertion order {@code ≤_G}
 * (Must §4), of JMC's extra causal edges (plan/state/state.md L3: spawn and join are part of
 * {@code porf}), and of the equality {@code G₁ ≈ G₂} the reference enumerator counts distinct
 * graphs by (plan/state/state.md D9).
 *
 * <p>The structural rejections pinned here are the ones {@link ExecutionGraph.Builder}'s Javadoc
 * claims: an event out of program order, an rf on a non-receive, an rf from a non-send. Everything
 * else the builder deliberately accepts, because whether a graph is <em>consistent</em> is
 * {@code org.mpi_sws.jmc.mp.consistency.MpConsistency}'s question, not the builder's.
 *
 * <p>{@code porfAcyclic()} is the last clause of Must Definition 3.3, checked here structurally and
 * again, as a consistency verdict, in {@code MpConsistencyWellFormednessTest}.
 */
// The two names 'po_isIndexOrderWithinAThread' and 'porfAcyclic_detectsACycleThroughRfAndPo'
// are fixed by the S2b assignment; 'AThread' and 'ACycle' are two consecutive capitals, and
// the repository config allows one (allowedAbbreviationLength=0). Measured: removing this
// annotation adds exactly those two warnings and no others.
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class ExecutionGraphTest {

    /** The builder appends within a thread; a gap or a repeat is program-order nonsense. */
    @Test
    void builder_rejectsEventOutOfProgramOrder() {
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        b.add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)));

        IllegalArgumentException gap =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> b.add(send(T1, 2, CommunicationModel.ASYN, T2, new Msg(2))),
                        "an index skipping one is not next in program order");
        assertTrue(gap.getMessage().contains("expected idx 1"), gap.getMessage());

        IllegalArgumentException repeat =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> b.add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(3))),
                        "an index already used is not next in program order");
        assertTrue(repeat.getMessage().contains("expected idx 1"), repeat.getMessage());

        // The accepted counterpart: the next index, and a fresh thread's index 0.
        b.add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)));
        b.add(recv(T2, 0, CommunicationModel.ASYN));
        ExecutionGraph g = b.build();
        assertEquals(2, g.thread(T1.raw()).size());
        assertEquals(1, g.thread(T2.raw()).size());
        assertEquals(0, g.thread(T3.raw()).size(), "a thread with no events has no events");
        assertEquals(3, g.size());
    }

    /** {@code rf ⊆ S⊥ × R}: the builder checks both ends' labels, and that both ends exist. */
    @Test
    void builder_rejectsRfOnNonReceive_andRfFromNonSend() {
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        b.add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)));
        b.add(recv(T2, 0, CommunicationModel.ASYN));

        IllegalArgumentException notRecv =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> b.readsFrom(key(T1, 0), key(T1, 0)));
        assertTrue(notRecv.getMessage().contains("is not a receive"), notRecv.getMessage());

        IllegalArgumentException notSend =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> b.readsFrom(key(T2, 0), key(T2, 0)));
        assertTrue(notSend.getMessage().contains("is not a send"), notSend.getMessage());

        IllegalArgumentException bottomOnSend =
                assertThrows(
                        IllegalArgumentException.class, () -> b.readsBottom(key(T1, 0)));
        assertTrue(bottomOnSend.getMessage().contains("is not a receive"), bottomOnSend.getMessage());

        IllegalArgumentException absent =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> b.readsFrom(key(T2, 7), key(T1, 0)));
        assertTrue(absent.getMessage().contains("no event"), absent.getMessage());

        IllegalArgumentException absentHb =
                assertThrows(IllegalArgumentException.class, () -> b.hb(key(T1, 0), key(T3, 0)));
        assertTrue(absentHb.getMessage().contains("no event"), absentHb.getMessage());

        // The accepted counterpart: the well-typed pair goes through.
        ExecutionGraph g = b.readsFrom(key(T2, 0), key(T1, 0)).build();
        assertEquals(Optional.of(key(T1, 0)), g.rf(key(T2, 0)));
    }

    /** {@code rf} maps a receive to a send <em>or</em> to {@code ⊥}; recording one clears the other. */
    @Test
    void builder_readsFromReplacesEarlierBottom_andViceVersa() {
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        b.add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)));
        b.add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)));
        b.add(recv(T2, 0, CommunicationModel.ASYN, false, ANY));

        ExecutionGraph bottom = b.readsBottom(key(T2, 0)).build();
        assertTrue(bottom.readsBottom(key(T2, 0)));
        assertEquals(Optional.empty(), bottom.rf(key(T2, 0)));

        ExecutionGraph reads = b.readsFrom(key(T2, 0), key(T1, 0)).build();
        assertFalse(reads.readsBottom(key(T2, 0)), "readsFrom clears the earlier ⊥");
        assertEquals(Optional.of(key(T1, 0)), reads.rf(key(T2, 0)));

        ExecutionGraph rereads = b.readsFrom(key(T2, 0), key(T1, 1)).build();
        assertEquals(Optional.of(key(T1, 1)), rereads.rf(key(T2, 0)), "rf is functional: it replaces");
        assertFalse(rereads.readsBottom(key(T2, 0)));

        ExecutionGraph again = b.readsBottom(key(T2, 0)).build();
        assertTrue(again.readsBottom(key(T2, 0)), "readsBottom clears the earlier rf");
        assertEquals(Optional.empty(), again.rf(key(T2, 0)));

        // The four graphs above are snapshots of one builder, so the earlier ones are unaffected.
        assertEquals(Optional.of(key(T1, 0)), reads.rf(key(T2, 0)));
        assertTrue(bottom.readsBottom(key(T2, 0)));
    }

    /** {@code po} is never stored: it is the index order within one thread, and nothing else. */
    @Test
    void po_isIndexOrderWithinAThread() {
        ExecutionGraph g =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(send(T1, 2, CommunicationModel.ASYN, T2, new Msg(3)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .build();

        assertTrue(g.poBefore(key(T1, 0), key(T1, 1)));
        assertTrue(g.poBefore(key(T1, 0), key(T1, 2)), "po is the whole index order, not the successor");
        assertFalse(g.poBefore(key(T1, 1), key(T1, 0)), "po is irreflexive and asymmetric");
        assertFalse(g.poBefore(key(T1, 1), key(T1, 1)));
        assertFalse(g.poBefore(key(T1, 0), key(T2, 0)), "po never crosses threads");
        assertFalse(g.poBefore(key(T2, 0), key(T1, 0)));

        // po ⊆ porf, and only the po pairs of one thread are in it.
        assertTrue(g.porfBefore(key(T1, 0), key(T1, 2)));
        assertFalse(g.porfBefore(key(T1, 0), key(T2, 0)), "no rf and no hb edge to T2 in this graph");

        assertEquals(
                List.of(key(T1, 0), key(T1, 1), key(T1, 2)),
                g.thread(T1.raw()).stream().map(GraphEvent::key).toList(),
                "thread(t) is in program order");
    }

    /**
     * {@code porf = (po ∪ rf ∪ hb)⁺}: the spawn and join edges of JMC's process model are causal
     * (plan/state/state.md L3). The control at the end rebuilds the same graph <em>without</em> the
     * two edges and shows the causality disappears with them.
     */
    @Test
    void porf_includesPo_rf_andHbEdges() {
        // T1 spawns T2 at T1:0 and joins it at T1:1; T2 sends to T3, which reads it.
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(nondet(T1, 0, 1, 0))
                        .add(block(T1, 1))
                        .add(send(T2, 0, CommunicationModel.ASYN, T3, new Msg(1)))
                        .add(nondet(T2, 1, 1, 0))
                        .add(recv(T3, 0, CommunicationModel.ASYN))
                        .readsFrom(key(T3, 0), key(T2, 0));
        ExecutionGraph g = b.hb(key(T1, 0), key(T2, 0)).hb(key(T2, 1), key(T1, 1)).build();

        assertEquals(
                Set.of(
                        new ExecutionGraph.Edge(key(T1, 0), key(T2, 0)),
                        new ExecutionGraph.Edge(key(T2, 1), key(T1, 1))),
                g.hb());

        assertTrue(g.porfBefore(key(T1, 0), key(T1, 1)), "po");
        assertTrue(g.porfBefore(key(T2, 0), key(T3, 0)), "rf");
        assertTrue(g.porfBefore(key(T1, 0), key(T2, 0)), "hb — the spawn edge");
        assertTrue(g.porfBefore(key(T2, 1), key(T1, 1)), "hb — the join edge");
        assertTrue(g.porfBefore(key(T1, 0), key(T3, 0)), "spawn, then po, then rf");
        assertTrue(g.porfBefore(key(T2, 0), key(T1, 1)), "po, then the join edge");
        assertFalse(g.porfBefore(key(T3, 0), key(T1, 1)), "T3's receive causes nothing here");
        assertFalse(g.porfBefore(key(T1, 1), key(T1, 0)));
        assertTrue(g.porfAcyclic());

        // Control: the same events and the same rf, with no hb edges at all.
        ExecutionGraph noHb =
                new ExecutionGraph.Builder()
                        .add(nondet(T1, 0, 1, 0))
                        .add(block(T1, 1))
                        .add(send(T2, 0, CommunicationModel.ASYN, T3, new Msg(1)))
                        .add(nondet(T2, 1, 1, 0))
                        .add(recv(T3, 0, CommunicationModel.ASYN))
                        .readsFrom(key(T3, 0), key(T2, 0))
                        .build();
        assertEquals(Set.of(), noHb.hb());
        assertFalse(noHb.porfBefore(key(T1, 0), key(T2, 0)), "without the spawn edge, T1 and T2 are concurrent");
        assertFalse(noHb.porfBefore(key(T2, 0), key(T1, 1)), "without the join edge, likewise");
        assertTrue(noHb.porfBefore(key(T2, 0), key(T3, 0)), "the rf edge is untouched");
    }

    /** {@code porf} is a transitive closure, so a three-hop path across two rf edges is one edge. */
    @Test
    void porf_isTransitive_acrossThreadsThroughRf() {
        ExecutionGraph g =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .add(send(T2, 1, CommunicationModel.ASYN, T3, new Msg(2)))
                        .add(recv(T3, 0, CommunicationModel.ASYN))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .readsFrom(key(T3, 0), key(T2, 1))
                        .build();

        assertTrue(g.porfBefore(key(T1, 0), key(T2, 0)));
        assertTrue(g.porfBefore(key(T2, 0), key(T2, 1)));
        assertTrue(g.porfBefore(key(T2, 1), key(T3, 0)));
        assertTrue(g.porfBefore(key(T1, 0), key(T3, 0)), "rf ; po ; rf, in one hop");
        assertFalse(g.porfBefore(key(T3, 0), key(T1, 0)), "and not the other way");
        assertFalse(g.porfBefore(key(T1, 0), key(T1, 0)), "porf is irreflexive here");
        assertTrue(g.porfAcyclic());
    }

    /** Two receives reading "future" sends close a {@code po ∪ rf} cycle — Def. 3.3's last clause. */
    @Test
    void porfAcyclic_detectsACycleThroughRfAndPo() {
        ExecutionGraph.Builder b =
                new ExecutionGraph.Builder()
                        .add(recv(T1, 0, CommunicationModel.ASYN))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .add(send(T2, 1, CommunicationModel.ASYN, T1, new Msg(1)))
                        .readsFrom(key(T2, 0), key(T1, 1));

        ExecutionGraph acyclic = b.build();
        assertTrue(acyclic.porfAcyclic(), "one rf edge alone closes nothing");

        ExecutionGraph cyclic = b.readsFrom(key(T1, 0), key(T2, 1)).build();
        assertFalse(cyclic.porfAcyclic(), "T1:0 ←rf T2:1 ←po T2:0 ←rf T1:1 ←po T1:0");
        for (long i = 0; i < 2; i++) {
            assertTrue(cyclic.porfBefore(key(T1, i), key(T1, i)), "T1:" + i + " is porf-before itself");
            assertTrue(cyclic.porfBefore(key(T2, i), key(T2, i)), "T2:" + i + " is porf-before itself");
        }
    }

    /** {@code readerOf} is {@code rf⁻¹}, and is empty for an unread send and for a {@code ⊥} read. */
    @Test
    void readerOf_isTheInverseOfRf() {
        ExecutionGraph g =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.ASYN))
                        .add(recv(T2, 1, CommunicationModel.ASYN, false, ANY))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .readsBottom(key(T2, 1))
                        .build();

        assertEquals(Optional.of(key(T2, 0)), g.readerOf(key(T1, 0)));
        assertEquals(Optional.empty(), g.readerOf(key(T1, 1)), "T1:1 is an unread send");
        assertEquals(Optional.of(key(T1, 0)), g.rf(key(T2, 0)));
        assertEquals(Optional.empty(), g.rf(key(T2, 1)), "a ⊥ read has no rf");
        assertTrue(g.readsBottom(key(T2, 1)));
        assertFalse(g.readsBottom(key(T2, 0)));

        for (GraphEvent s : g.sends()) {
            Optional<EventKey> r = g.readerOf(s.key());
            assertEquals(
                    r.isPresent() ? Optional.of(s.key()) : Optional.empty(),
                    r.flatMap(g::rf),
                    "rf(readerOf(s)) = s for " + s.key());
        }
        assertEquals(List.of(key(T1, 0), key(T1, 1)), g.sends().stream().map(GraphEvent::key).toList());
        assertEquals(List.of(key(T2, 0), key(T2, 1)), g.receives().stream().map(GraphEvent::key).toList());
    }

    /** {@code ≈} is up to insertion order: the same events added in two orders give one canon. */
    @Test
    void canon_ignoresInsertionOrder() {
        ExecutionGraph a =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(recv(T2, 1, CommunicationModel.P2P))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .readsFrom(key(T2, 1), key(T1, 1))
                        .build();
        ExecutionGraph b =
                new ExecutionGraph.Builder()
                        .add(recv(T2, 0, CommunicationModel.P2P))
                        .add(send(T1, 0, CommunicationModel.P2P, T2, new Msg(1)))
                        .add(recv(T2, 1, CommunicationModel.P2P))
                        .add(send(T1, 1, CommunicationModel.P2P, T2, new Msg(2)))
                        .readsFrom(key(T2, 1), key(T1, 1))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();

        assertNotEquals(
                a.eventsInInsertionOrder().stream().map(GraphEvent::key).toList(),
                b.eventsInInsertionOrder().stream().map(GraphEvent::key).toList(),
                "the two builds really do differ in ≤_G");
        assertEquals(0, a.insertionIndex(key(T1, 0)));
        assertEquals(1, b.insertionIndex(key(T1, 0)));
        assertEquals(a.canon(), b.canon(), "≈ ignores ≤_G");
        assertEquals(a.canon().hashCode(), b.canon().hashCode());
        assertThrows(IllegalArgumentException.class, () -> a.insertionIndex(key(T3, 0)));
    }

    /** The receive predicate is behaviour, not identity: it is in neither {@code equals} nor canon. */
    @Test
    void canon_ignoresReceivePredicates() {
        ExecutionGraph selective =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.ASYN, true, msgIs(1)))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();
        ExecutionGraph unselective =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(recv(T2, 0, CommunicationModel.ASYN, true, ANY))
                        .readsFrom(key(T2, 0), key(T1, 0))
                        .build();

        assertEquals(selective.canon(), unselective.canon());
        assertEquals(
                selective.event(key(T2, 0)),
                unselective.event(key(T2, 0)),
                "GraphEvent identity is the key and the label only");
        assertEquals(
                selective.event(key(T2, 0)).hashCode(), unselective.event(key(T2, 0)).hashCode());

        // The predicates really are different, and the graph still carries each one.
        assertTrue(selective.event(key(T2, 0)).accepts(new Msg(1)));
        assertFalse(selective.event(key(T2, 0)).accepts(new Msg(2)));
        assertTrue(unselective.event(key(T2, 0)).accepts(new Msg(2)));
        assertThrows(
                IllegalStateException.class, () -> selective.event(key(T1, 0)).accepts(new Msg(1)));
    }

    /** Canon separates the four things it does record: rf, {@code ⊥}, a nondet choice, and hb. */
    @Test
    void canon_distinguishesRf_bottom_andNondetChoice() {
        ExecutionGraph.Builder base =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(nondet(T2, 0, 2, 0))
                        .add(recv(T2, 1, CommunicationModel.ASYN, false, ANY));

        GraphCanon readsFirst = new ExecutionGraph.Builder(base.build())
                .readsFrom(key(T2, 1), key(T1, 0))
                .build()
                .canon();
        GraphCanon readsSecond = new ExecutionGraph.Builder(base.build())
                .readsFrom(key(T2, 1), key(T1, 1))
                .build()
                .canon();
        GraphCanon readsBottom = new ExecutionGraph.Builder(base.build())
                .readsBottom(key(T2, 1))
                .build()
                .canon();

        // Both sends carry the same value, so ONLY rf separates the first two.
        assertNotEquals(readsFirst, readsSecond, "rf is part of ≈");
        assertNotEquals(readsFirst, readsBottom, "reading ⊥ is not reading a send");
        assertNotEquals(readsSecond, readsBottom);
        assertEquals(Set.of(key(T2, 1)), readsBottom.bottoms());
        assertEquals(Set.of(), readsFirst.bottoms());

        // The nondet choice, with everything else held fixed.
        GraphCanon otherChoice =
                new ExecutionGraph.Builder()
                        .add(send(T1, 0, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(send(T1, 1, CommunicationModel.ASYN, T2, new Msg(1)))
                        .add(nondet(T2, 0, 2, 1))
                        .add(recv(T2, 1, CommunicationModel.ASYN, false, ANY))
                        .readsFrom(key(T2, 1), key(T1, 0))
                        .build()
                        .canon();
        assertNotEquals(readsFirst, otherChoice, "a different nondet choice is a different graph");

        // The hb edges, with everything else held fixed.
        GraphCanon withHb = new ExecutionGraph.Builder(base.build())
                .readsFrom(key(T2, 1), key(T1, 0))
                .hb(key(T1, 0), key(T2, 0))
                .build()
                .canon();
        assertNotEquals(readsFirst, withHb, "a spawn edge is part of ≈");
        assertEquals(Set.of(new ExecutionGraph.Edge(key(T1, 0), key(T2, 0))), withHb.hb());
    }

    /**
     * Discriminating construction for this class: canon is not blind to a label difference. The
     * insertion-order and predicate tests above assert that two graphs are <em>equal</em>; without
     * this, a {@code canon()} that returned a constant would pass all three. Each pair below differs
     * in exactly one label component.
     */
    @Test
    void canon_discriminatesLabelDifferences() {
        GraphCanon value = twoSends(CommunicationModel.P2P, new Msg(1), true);
        GraphCanon otherValue = twoSends(CommunicationModel.P2P, new Msg(9), true);
        GraphCanon otherModel = twoSends(CommunicationModel.MBOX, new Msg(1), true);
        GraphCanon nonBlocking = twoSends(CommunicationModel.P2P, new Msg(1), false);

        assertNotEquals(value, otherValue, "the value of a send is part of its label");
        assertNotEquals(value, otherModel, "the model of a send and a receive is part of its label");
        assertNotEquals(value, nonBlocking, "β ∈ {b, nb} is part of a receive's label");
        assertEquals(value, twoSends(CommunicationModel.P2P, new Msg(1), true), "…and canon is a value");
    }

    /** One thread sending twice and one reading once, parameterized by the label components. */
    private static GraphCanon twoSends(CommunicationModel model, Msg second, boolean blocking) {
        return new ExecutionGraph.Builder()
                .add(send(T1, 0, model, T2, new Msg(1)))
                .add(send(T1, 1, model, T2, second))
                .add(recv(T2, 0, model, blocking, ANY))
                .readsFrom(key(T2, 0), key(T1, 0))
                .build()
                .canon();
    }
}
