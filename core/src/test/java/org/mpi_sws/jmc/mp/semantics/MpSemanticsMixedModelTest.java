package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;
import static org.mpi_sws.jmc.mp.MpTestSupport.T1;
import static org.mpi_sws.jmc.mp.MpTestSupport.T2;
import static org.mpi_sws.jmc.mp.MpTestSupport.T3;
import static org.mpi_sws.jmc.mp.MpTestSupport.env;
import static org.mpi_sws.jmc.mp.MpTestSupport.envelopes;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.consistency.TaskClocks;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;

import java.util.List;

/**
 * Pins the "<b>Mixed models</b>" bullet of plan/state/state.md §5.2: "Buffers disjoint per model,
 * rules checked per model — mirrors Must §1.3 — <b>but clocks are shared across models</b>", and
 * the paragraph above the delivery table: "Every message of every model carries the sender's vector
 * clock at emission time, and every delivery of every model joins the receiver's clock with it."
 *
 * <p>Must paper reference: consistency is checked per model on that model's events (the
 * Consistency definition after Definition 3.7, §1.3 of plan/state/must-algorithm.md), while
 * {@code porf} — and hence {@code cd}'s send order {@code so ≜ [S^cd]; porf; [S^cd]} of
 * Definition 3.6 — runs through the reads-from edges of <em>every</em> model (Example 2.12).
 * Clocks carried only on {@code cd} messages is the defect review round 1, M1 records.
 */
public class MpSemanticsMixedModelTest {

    // Clock mode: withClocks() wherever a cd send appears — which is everywhere except the first
    // half of the discriminating test, whose program has no cd send and therefore uses
    // withoutClocks(). Under withClocks() every model's emission must be stamped, so the p2p and
    // asyn sends below carry stamps too; that is the cross-model clock discipline this class pins.

    @Test
    void deliverable_buffersAreDisjointPerModel() {
        // The same sender, the same destination, the same payload — one send per model.
        Envelope asyn = env(T1, 0, T3, CommunicationModel.ASYN, new Msg(7));
        Envelope p2p = env(T1, 1, T3, CommunicationModel.P2P, new Msg(7));
        Envelope cd = env(T1, 2, T3, CommunicationModel.CD, new Msg(7));
        Envelope mbox = env(T1, 3, T3, CommunicationModel.MBOX, new Msg(7));

        TaskClocks.Emission stamp = TaskClocks.EMPTY.emit(T1.raw());
        Buffers b =
                Buffers.withClocks()
                        .emit(asyn, stamp.stamp())
                        .emit(p2p, stamp.stamp())
                        .emit(cd, stamp.stamp())
                        .emit(mbox, stamp.stamp());

        assertEquals(4, b.size());
        assertEquals(
                List.of(asyn), envelopes(Delivery.deliverable(b, CommunicationModel.ASYN, T3, ANY)));
        assertEquals(
                List.of(p2p), envelopes(Delivery.deliverable(b, CommunicationModel.P2P, T3, ANY)));
        assertEquals(List.of(cd), envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)));
        assertEquals(
                List.of(mbox), envelopes(Delivery.deliverable(b, CommunicationModel.MBOX, T3, ANY)));

        // A receive under one model neither consumes from nor unblocks any other model's buffer.
        Buffers afterMbox = b.remove(mbox);
        assertEquals(3, afterMbox.size());
        assertEquals(
                List.of(asyn),
                envelopes(Delivery.deliverable(afterMbox, CommunicationModel.ASYN, T3, ANY)));
        assertEquals(
                List.of(),
                envelopes(Delivery.deliverable(afterMbox, CommunicationModel.MBOX, T3, ANY)));

        // Removing from the wrong model's buffer is an error, not a silent no-op: the envelope
        // carries its model, so this proves the four buffers really are separate stores.
        assertThrows(IllegalArgumentException.class, () -> afterMbox.remove(mbox));
    }

    /**
     * {@code porf} crosses models (review round 1, M1). Two {@code cd} sends to one destination are
     * made causally related by a {@code p2p} message between their senders; the {@code cd} rule
     * must then order them. Every stamp is produced by {@link TaskClocks}.
     */
    @Test
    void clocks_areStampedAndJoinedAcrossModels() {
        TaskClocks clocks = TaskClocks.EMPTY;

        TaskClocks.Emission first = clocks.emit(T1.raw());
        Envelope cd1 = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));
        clocks = first.clocks();

        // The causal link is a p2p message — a model with no cd events at all.
        TaskClocks.Emission link = clocks.emit(T1.raw());
        Envelope p2pLink = env(T1, 1, T2, CommunicationModel.P2P, new Msg(9));
        clocks = link.clocks();

        Buffers b = Buffers.withClocks().emit(cd1, first.stamp()).emit(p2pLink, link.stamp());

        // T2 receives the p2p message. The delivery joins T2's clock with the *p2p* stamp.
        assertEquals(
                List.of(p2pLink),
                envelopes(Delivery.deliverable(b, CommunicationModel.P2P, T2, ANY)));
        b = b.remove(p2pLink);
        clocks = clocks.deliver(T2.raw(), link.stamp());
        assertTrue(
                first.stamp().leq(clocks.clockOf(T2.raw())),
                "T2 has absorbed T1's pre-send clock through a non-cd reads-from edge");

        TaskClocks.Emission second = clocks.emit(T2.raw());
        Envelope cd2 = env(T2, 0, T3, CommunicationModel.CD, new Msg(2));
        b = b.emit(cd2, second.stamp());

        assertTrue(first.stamp().lessThan(second.stamp()));
        assertEquals(
                List.of(cd1),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)),
                "the cd rule sees a porf path that runs entirely through p2p");
    }

    /**
     * Discriminating construction for this class, in two halves.
     *
     * <p>First half — per-model rules: the {@code p2p} restriction on a sender's two sends does not
     * touch the {@code asyn} buffer, so {@code deliverable_buffersAreDisjointPerModel} is not
     * passing because every buffer happens to hold one message.
     *
     * <p>Second half — the M1 defect: {@code clocks_areStampedAndJoinedAcrossModels} is rebuilt
     * with the stamp T2 would have had if clocks were carried only on {@code cd} messages (the
     * revision 3 design), and both {@code cd} messages then become deliverable — the monitor
     * misordering M1 describes.
     */
    @Test
    void deliverable_discriminatesPerModelRulesAndCrossModelClocks() {
        // First half: a program with no cd send at all, hence withoutClocks() and no stamps. The
        // point being made is about the p2p and asyn rules, which read no clock.
        Buffers mixed =
                Buffers.withoutClocks()
                        .emit(env(T1, 0, T3, CommunicationModel.P2P, new Msg(1)), null)
                        .emit(env(T1, 1, T3, CommunicationModel.P2P, new Msg(2)), null)
                        .emit(env(T1, 2, T3, CommunicationModel.ASYN, new Msg(1)), null)
                        .emit(env(T1, 3, T3, CommunicationModel.ASYN, new Msg(2)), null);
        assertEquals(
                1,
                Delivery.deliverable(mixed, CommunicationModel.P2P, T3, ANY).size(),
                "p2p: the sender's second send is blocked by its first");
        assertEquals(
                2,
                Delivery.deliverable(mixed, CommunicationModel.ASYN, T3, ANY).size(),
                "asyn: the very same pair from the same sender is unconstrained");

        // Deliberately wrong: clocks on cd messages only, so the p2p link carries no causality.
        TaskClocks.Emission first = TaskClocks.EMPTY.emit(T1.raw());
        Envelope cd1 = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));

        // T2 never joins anything, because the message it read was not a cd message.
        TaskClocks.Emission secondNoCrossModelClock = first.clocks().emit(T2.raw());
        Envelope cd2 = env(T2, 0, T3, CommunicationModel.CD, new Msg(2));

        Buffers wrong =
                Buffers.withClocks()
                        .emit(cd1, first.stamp())
                        .emit(cd2, secondNoCrossModelClock.stamp());
        assertTrue(first.stamp().concurrentWith(secondNoCrossModelClock.stamp()));
        assertEquals(
                List.of(cd1, cd2),
                envelopes(Delivery.deliverable(wrong, CommunicationModel.CD, T3, ANY)),
                "clocks carried only on cd under-approximate the cd constraint and admit a "
                        + "delivery Must forbids — review round 1, M1");
    }
}
