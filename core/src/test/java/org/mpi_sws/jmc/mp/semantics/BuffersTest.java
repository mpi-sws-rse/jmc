package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.MpTestSupport.T1;
import static org.mpi_sws.jmc.mp.MpTestSupport.T2;
import static org.mpi_sws.jmc.mp.MpTestSupport.T3;
import static org.mpi_sws.jmc.mp.MpTestSupport.T4;
import static org.mpi_sws.jmc.mp.MpTestSupport.env;
import static org.mpi_sws.jmc.mp.MpTestSupport.envelopes;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.consistency.VectorClock;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/**
 * Pins the canonical forms of plan/state/state.md §5.2 (the "Buffer structure (canonical form)"
 * column of the delivery-rule table) and §5.3: "two buffer values are {@code equals} exactly when
 * they are behaviourally the same", which is what state merging in the explicit-state backend
 * depends on.
 *
 * <p>Concretely: {@code asyn} is a multiset per destination and {@code p2p} a FIFO queue per
 * {@code (sender, destination)}, so neither records the order in which unrelated messages happened
 * to be emitted; {@code mbox} is a FIFO list per destination in global emission order, so it does.
 * Also pins {@link Buffers#pending} for {@code P2P}, whose "per-sender queues concatenated in
 * sender order, each in emission order" shape is the precondition
 * {@link Delivery}'s {@code p2p} rule relies on to call the first match it sees a sender's
 * earliest.
 *
 * <p><b>Equality is the {@code (src, dst, model, value, clock)} projection</b> (S2 review round 1,
 * M1): {@link Envelope#sendIndex()} is a per-sender monotone counter that no §5.2 rule reads, so
 * including it in identity would make every pass through a cycle holding an in-flight message a
 * fresh state and would stop merging from ever closing a ping-pong loop.
 * {@code equals_ignoresSendIndex_asynP2pAndMbox} is that property; the discriminating case at the
 * end shows equality is nonetheless neither value-blind nor clock-blind.
 *
 * <p><b>Clock mode</b> (S2 review round 1, m4): {@link Buffers#emit} enforces the whole stamping
 * invariant rather than half of it, and the two {@code emit_…SendWith…} tests below are that
 * two-sided check. The tests in this class that do not exercise clocks use
 * {@link Buffers#withoutClocks()}, which is the mode of a program with no {@code cd} sends —
 * exactly the programs whose canonical forms this class is about.
 */
public class BuffersTest {

    /** An envelope to {@link org.mpi_sws.jmc.mp.MpTestSupport#T3} carrying {@code Msg(n)}. */
    private static Envelope toT3(CommunicationModel model, Tid src, long idx, int n) {
        return env(src, idx, T3, model, new Msg(n));
    }

    @Test
    void asyn_equalMultisetsAreEqualRegardlessOfEmissionOrder() {
        Envelope fromT1 = toT3(CommunicationModel.ASYN, T1, 0, 1);
        Envelope fromT2 = toT3(CommunicationModel.ASYN, T2, 0, 2);

        Buffers oneOrder = Buffers.withoutClocks().emit(fromT1, null).emit(fromT2, null);
        Buffers otherOrder = Buffers.withoutClocks().emit(fromT2, null).emit(fromT1, null);

        assertEquals(oneOrder, otherOrder, "asyn is a multiset: emission order is not semantic");
        assertEquals(oneOrder.hashCode(), otherOrder.hashCode());
        assertEquals(
                envelopes(oneOrder.pending(CommunicationModel.ASYN, T3)),
                envelopes(otherOrder.pending(CommunicationModel.ASYN, T3)),
                "and both are stored in the same canonical order");

        // Two messages from one sender: still order-insensitive under asyn.
        Envelope alsoFromT1 = toT3(CommunicationModel.ASYN, T1, 1, 3);
        assertEquals(
                oneOrder.emit(alsoFromT1, null),
                Buffers.withoutClocks().emit(alsoFromT1, null).emit(fromT2, null).emit(fromT1, null));
    }

    @Test
    void p2p_equalPerChannelQueuesAreEqualRegardlessOfCrossSenderOrder() {
        Envelope t1First = toT3(CommunicationModel.P2P, T1, 0, 1);
        Envelope t1Second = toT3(CommunicationModel.P2P, T1, 1, 2);
        Envelope t2Only = toT3(CommunicationModel.P2P, T2, 0, 3);

        // T1's two sends keep their relative order in both; T2's send moves across them.
        Buffers a = Buffers.withoutClocks().emit(t1First, null).emit(t1Second, null).emit(t2Only, null);
        Buffers b = Buffers.withoutClocks().emit(t2Only, null).emit(t1First, null).emit(t1Second, null);
        Buffers c = Buffers.withoutClocks().emit(t1First, null).emit(t2Only, null).emit(t1Second, null);

        assertEquals(a, b, "the relative order of different senders' messages is not semantic");
        assertEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());

        // But the order within one channel is.
        Buffers swappedChannel =
                Buffers.withoutClocks().emit(t1Second, null).emit(t1First, null).emit(t2Only, null);
        assertNotEquals(a, swappedChannel, "per-channel FIFO order is semantic");
        assertEquals(
                List.of(t1Second, t1First),
                envelopes(swappedChannel.pendingP2p(T1, T3)),
                "the queue records the order it was emitted in, whatever that was");
    }

    @Test
    void mbox_differentEmissionOrdersAreNotEqual() {
        Envelope fromT1 = toT3(CommunicationModel.MBOX, T1, 0, 1);
        Envelope fromT2 = toT3(CommunicationModel.MBOX, T2, 0, 2);

        Buffers oneOrder = Buffers.withoutClocks().emit(fromT1, null).emit(fromT2, null);
        Buffers otherOrder = Buffers.withoutClocks().emit(fromT2, null).emit(fromT1, null);

        assertNotEquals(
                oneOrder,
                otherOrder,
                "mbox stores one global emission order per destination; swapping it is a "
                        + "behaviourally different state");
        assertEquals(List.of(fromT1, fromT2), envelopes(oneOrder.pending(CommunicationModel.MBOX, T3)));
        assertEquals(
                List.of(fromT2, fromT1), envelopes(otherOrder.pending(CommunicationModel.MBOX, T3)));

        // Only the relative order is stored — no absolute counter — so a mailbox drained and
        // refilled equals a fresh one holding the same message (state.md §5.2, §5.3).
        Buffers drained = oneOrder.remove(fromT1).remove(fromT2).emit(fromT1, null);
        assertEquals(Buffers.withoutClocks().emit(fromT1, null), drained);
    }

    /**
     * Review round 1, M1: two buffers whose entries agree on {@code (src, dst, model, value,
     * clock)} are {@code equals} however the sends were indexed, in all three canonical shapes —
     * the asyn multiset, the p2p per-channel sequence and the mbox per-destination sequence.
     *
     * <p>The last case in each block is the one merging actually needs: a channel or mailbox that
     * was drained and refilled with the same value equals a fresh one holding it. Without that, a
     * client/server ping-pong loop produces {@code {Req#0}}, {@code {Req#1}}, … forever and the
     * visited set of a finite-behaviour program never closes.
     */
    @Test
    void equals_ignoresSendIndex_asynP2pAndMbox() {
        // asyn — a bag of projections.
        Buffers asynLow = Buffers.withoutClocks().emit(toT3(CommunicationModel.ASYN, T1, 0, 7), null);
        Buffers asynHigh = Buffers.withoutClocks().emit(toT3(CommunicationModel.ASYN, T1, 4, 7), null);
        assertEquals(asynLow, asynHigh, "sendIndex is storage, not identity");
        assertEquals(asynLow.hashCode(), asynHigh.hashCode());
        assertEquals(
                asynLow.remove(toT3(CommunicationModel.ASYN, T1, 0, 7))
                        .emit(toT3(CommunicationModel.ASYN, T1, 9, 7), null),
                asynHigh,
                "a bag drained and refilled with the same value equals a fresh one");

        // p2p — a sequence of projections per (sender, destination) channel.
        Buffers p2pLow =
                Buffers.withoutClocks()
                        .emit(toT3(CommunicationModel.P2P, T1, 0, 1), null)
                        .emit(toT3(CommunicationModel.P2P, T1, 1, 2), null);
        Buffers p2pHigh =
                Buffers.withoutClocks()
                        .emit(toT3(CommunicationModel.P2P, T1, 5, 1), null)
                        .emit(toT3(CommunicationModel.P2P, T1, 9, 2), null);
        assertEquals(p2pLow, p2pHigh, "the channel's value sequence is the state, not the indices");
        assertEquals(p2pLow.hashCode(), p2pHigh.hashCode());

        Buffers refilledChannel =
                p2pLow.remove(toT3(CommunicationModel.P2P, T1, 0, 1))
                        .remove(toT3(CommunicationModel.P2P, T1, 1, 2))
                        .emit(toT3(CommunicationModel.P2P, T1, 2, 1), null);
        assertEquals(
                Buffers.withoutClocks().emit(toT3(CommunicationModel.P2P, T1, 0, 1), null),
                refilledChannel,
                "a drained and refilled p2p channel equals a fresh one holding the same value — "
                        + "the ping-pong cycle closes (review round 1, M1)");

        // mbox — a sequence of projections per destination.
        Buffers mboxLow =
                Buffers.withoutClocks()
                        .emit(toT3(CommunicationModel.MBOX, T1, 0, 1), null)
                        .emit(toT3(CommunicationModel.MBOX, T2, 0, 2), null);
        Buffers mboxHigh =
                Buffers.withoutClocks()
                        .emit(toT3(CommunicationModel.MBOX, T1, 3, 1), null)
                        .emit(toT3(CommunicationModel.MBOX, T2, 8, 2), null);
        assertEquals(mboxLow, mboxHigh, "the mailbox's value sequence is the state");
        assertEquals(mboxLow.hashCode(), mboxHigh.hashCode());

        // And the two indices are still in storage: remove() names one send, not a value.
        assertEquals(
                List.of(toT3(CommunicationModel.MBOX, T1, 3, 1), toT3(CommunicationModel.MBOX, T2, 8, 2)),
                envelopes(mboxHigh.pending(CommunicationModel.MBOX, T3)));
        assertThrows(
                IllegalArgumentException.class,
                () -> mboxHigh.remove(toT3(CommunicationModel.MBOX, T1, 0, 1)),
                "equality ignores the index; removal does not");
    }

    @Test
    void emit_ofAlreadyPendingSend_throws() {
        Envelope first = toT3(CommunicationModel.ASYN, T1, 0, 1);
        Buffers b = Buffers.withoutClocks().emit(first, null);

        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> b.emit(first, null))
                        .getMessage()
                        .contains("already pending"),
                "a send identity emitted twice can only be a backend bug (review round 1, m5)");

        // The identity is (src, sendIndex) alone, so a re-emission is caught whatever else differs:
        // a different value, a different destination, or a different model's buffer.
        assertThrows(
                IllegalArgumentException.class,
                () -> b.emit(toT3(CommunicationModel.ASYN, T1, 0, 99), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> b.emit(env(T1, 0, T4, CommunicationModel.ASYN, new Msg(1)), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> b.emit(toT3(CommunicationModel.P2P, T1, 0, 1), null),
                "the check spans all four buffers: one task's send indices are one sequence");

        // What is not a duplicate: another sender's send with the same index, the same sender's
        // next index, and the same index once the first send has been delivered.
        assertEquals(2, b.emit(toT3(CommunicationModel.ASYN, T2, 0, 1), null).size());
        assertEquals(2, b.emit(toT3(CommunicationModel.ASYN, T1, 1, 1), null).size());
        assertEquals(1, b.remove(first).emit(first, null).size());
    }

    /**
     * Review round 1, m4, one half: with clocks enabled every emission of <em>every</em> model must
     * carry a stamp. The {@code P2P} case is the M1 shape the core previously waved through — an
     * unstamped {@code p2p} send in a program that does use {@code cd} — which is why the check is
     * not restricted to {@code cd} sends.
     */
    @Test
    void emit_unstampedSendWithClocksEnabled_throws() {
        VectorClock stamp = VectorClock.EMPTY.tick(T1.raw());

        for (CommunicationModel model : CommunicationModel.values()) {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> Buffers.withClocks().emit(toT3(model, T1, 0, 1), null));
            assertTrue(
                    thrown.getMessage().contains("clocks enabled"),
                    "an unstamped " + model + " send must fail loudly — was: " + thrown.getMessage());

            // The same send with a stamp is accepted, so the rejection is about the stamp.
            assertEquals(1, Buffers.withClocks().emit(toT3(model, T1, 0, 1), stamp).size());
        }
    }

    /**
     * Review round 1, m4, the other half: with clocks disabled no emission may carry a stamp (that
     * would be a backend that enabled clocks in one place and not the other), and a {@code cd} send
     * is rejected outright whether or not it is stamped.
     */
    @Test
    void emit_stampedSendWithClocksDisabled_throws() {
        VectorClock stamp = VectorClock.EMPTY.tick(T1.raw());

        for (CommunicationModel model : CommunicationModel.values()) {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> Buffers.withoutClocks().emit(toT3(model, T1, 0, 1), stamp));
            assertTrue(
                    thrown.getMessage().contains("clocks disabled but"),
                    "a stamped " + model + " send must fail loudly — was: " + thrown.getMessage());
        }

        // A cd send is an error in this mode even unstamped: the program declared it uses no cd.
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () -> Buffers.withoutClocks().emit(toT3(CommunicationModel.CD, T1, 0, 1), null))
                        .getMessage()
                        .contains("cd send"));

        // The other three models, unstamped, are exactly what this mode accepts.
        assertEquals(1, Buffers.withoutClocks().emit(toT3(CommunicationModel.ASYN, T1, 0, 1), null).size());
        assertEquals(1, Buffers.withoutClocks().emit(toT3(CommunicationModel.P2P, T1, 0, 1), null).size());
        assertEquals(1, Buffers.withoutClocks().emit(toT3(CommunicationModel.MBOX, T1, 0, 1), null).size());
    }

    @Test
    void remove_ofNonPendingMessage_throws() {
        Envelope pending = toT3(CommunicationModel.ASYN, T1, 0, 1);
        Buffers b = Buffers.withoutClocks().emit(pending, null);

        // Never emitted at all.
        Envelope never = toT3(CommunicationModel.ASYN, T1, 1, 1);
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> b.remove(never))
                        .getMessage()
                        .contains("not pending"));

        // Same send, wrong model — the envelope names its own buffer.
        Envelope wrongModel = toT3(CommunicationModel.P2P, T1, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> b.remove(wrongModel));

        // Same send, wrong destination.
        Envelope wrongDst = env(T1, 0, T4, CommunicationModel.ASYN, new Msg(1));
        assertThrows(IllegalArgumentException.class, () -> b.remove(wrongDst));

        // Removing twice throws the second time: a message is read once.
        Buffers empty = b.remove(pending);
        assertTrue(empty.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> empty.remove(pending));

        assertThrows(NullPointerException.class, () -> b.remove(null));
    }

    @Test
    void pending_p2p_concatenatesPerSenderQueuesInEmissionOrder() {
        Envelope t2Only = toT3(CommunicationModel.P2P, T2, 0, 1);
        Envelope t1First = toT3(CommunicationModel.P2P, T1, 0, 2);
        Envelope t1Second = toT3(CommunicationModel.P2P, T1, 1, 3);
        Envelope toSomeoneElse = env(T1, 2, T4, CommunicationModel.P2P, new Msg(4));

        // Emitted T2, T1, T1, T1->T4 — deliberately not in sender order.
        Buffers b =
                Buffers.withoutClocks()
                        .emit(t2Only, null)
                        .emit(t1First, null)
                        .emit(t1Second, null)
                        .emit(toSomeoneElse, null);

        assertEquals(
                List.of(t1First, t1Second, t2Only),
                envelopes(b.pending(CommunicationModel.P2P, T3)),
                "sender order across channels, emission order within each — and only messages "
                        + "for this destination");
        assertEquals(List.of(t1First, t1Second), envelopes(b.pendingP2p(T1, T3)));
        assertEquals(List.of(t2Only), envelopes(b.pendingP2p(T2, T3)));
        assertEquals(List.of(), envelopes(b.pendingP2p(T3, T1)), "an empty channel is not null");
        assertEquals(List.of(toSomeoneElse), envelopes(b.pending(CommunicationModel.P2P, T4)));

        // The returned views are unmodifiable — a backend cannot corrupt a buffer through them.
        assertThrows(
                UnsupportedOperationException.class,
                () -> b.pending(CommunicationModel.P2P, T3).add(null));
    }

    /**
     * Discriminating construction for this class, in three parts.
     *
     * <p>First: {@code mbox_differentEmissionOrdersAreNotEqual} would also pass if
     * {@link Buffers#equals} were order-sensitive everywhere (or simply broken and never equal);
     * this constructs exactly that alternative and shows it is false — the same two sends, in the
     * same two emission orders, are <em>equal</em> under {@code asyn} and <em>unequal</em> under
     * {@code mbox}, in one pair of buffers.
     *
     * <p>Second and third: {@code equals_ignoresSendIndex_asynP2pAndMbox} would also pass if
     * equality ignored the payload, or the stamp, as well as the index. Two buffers differing only
     * in a message's value, and two differing only in its clock, are not equal — so dropping
     * {@code sendIndex} from identity dropped exactly one component.
     */
    @Test
    void equals_discriminatesMboxOrderFromAsynMultiset() {
        Envelope asynT1 = toT3(CommunicationModel.ASYN, T1, 0, 1);
        Envelope asynT2 = toT3(CommunicationModel.ASYN, T2, 0, 2);
        Envelope mboxT1 = toT3(CommunicationModel.MBOX, T1, 1, 1);
        Envelope mboxT2 = toT3(CommunicationModel.MBOX, T2, 1, 2);

        Buffers asynOnly = Buffers.withoutClocks().emit(asynT1, null).emit(asynT2, null);
        Buffers asynSwapped = Buffers.withoutClocks().emit(asynT2, null).emit(asynT1, null);
        assertEquals(asynOnly, asynSwapped);

        Buffers withMbox = asynOnly.emit(mboxT1, null).emit(mboxT2, null);
        Buffers withMboxSwapped = asynSwapped.emit(mboxT2, null).emit(mboxT1, null);
        assertNotEquals(
                withMbox,
                withMboxSwapped,
                "adding the same two sends under mbox, in the swapped order, breaks the equality "
                        + "that held under asyn — so Buffers.equals is neither order-blind nor "
                        + "order-sensitive everywhere: it is canonical per model");

        // Equality is not value-blind: same sender, index and destination, different payload.
        assertNotEquals(
                Buffers.withoutClocks().emit(toT3(CommunicationModel.ASYN, T1, 0, 1), null),
                Buffers.withoutClocks().emit(toT3(CommunicationModel.ASYN, T1, 0, 2), null),
                "the value is part of the projection");

        // Nor clock-blind, with clocks enabled: same send, same value, different stamp.
        VectorClock early = VectorClock.EMPTY.tick(T1.raw());
        VectorClock late = early.tick(T1.raw());
        assertNotEquals(
                Buffers.withClocks().emit(toT3(CommunicationModel.CD, T1, 0, 1), early),
                Buffers.withClocks().emit(toT3(CommunicationModel.CD, T1, 0, 1), late),
                "the stamp is part of the projection — it is what the cd rule reads");

        // The clock mode itself is part of equality: it is a property of the program.
        assertNotEquals(Buffers.withClocks(), Buffers.withoutClocks());
        assertTrue(Buffers.withClocks().isEmpty() && Buffers.withoutClocks().isEmpty());
    }
}
