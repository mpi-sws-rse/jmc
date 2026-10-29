package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;
import static org.mpi_sws.jmc.mp.MpTestSupport.T1;
import static org.mpi_sws.jmc.mp.MpTestSupport.T2;
import static org.mpi_sws.jmc.mp.MpTestSupport.T3;
import static org.mpi_sws.jmc.mp.MpTestSupport.env;
import static org.mpi_sws.jmc.mp.MpTestSupport.envelopes;
import static org.mpi_sws.jmc.mp.MpTestSupport.msgIs;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;

import java.util.List;

/**
 * Pins the {@code P2P} row of the delivery-rule table in plan/state/state.md §5.2: "{@code g(m)}
 * and there is <b>no</b> pending {@code m' ∈ Q[src(m)→i]} emitted before {@code m} with
 * {@code g(m')} — the earliest <em>matching</em> pending message from that sender. Non-matching
 * earlier messages are skipped and stay pending."
 *
 * <p>Must paper reference: {@code consistent_p2p}, Definition 3.5, with
 * {@code so ≜ [S^p2p]; po; [S^p2p]}. The "earliest matching pending at the moment of delivery"
 * formulation is Lemma 5.2.1 of state.md §5.2, which enforces the paper's clauses (b) and (c)
 * jointly. The skipping of non-matching earlier messages is the {@code ∩ mval} restriction —
 * Must.pdf §2.3.3, "selective receives as an rf filter, not an assume".
 */
public class MpSemanticsP2pTest {

    // Clock mode: withoutClocks(). The p2p rule is stated over po on one channel and reads no
    // stamp; these programs contain no cd send, so clocks are off and the emissions are unstamped.

    // T1 -> T3: Msg(1) then Msg(2).  T2 -> T3: Msg(3).
    private static final Envelope T1_FIRST = env(T1, 0, T3, CommunicationModel.P2P, new Msg(1));
    private static final Envelope T1_SECOND = env(T1, 1, T3, CommunicationModel.P2P, new Msg(2));
    private static final Envelope T2_ONLY = env(T2, 0, T3, CommunicationModel.P2P, new Msg(3));

    private static Buffers twoSenders() {
        return Buffers.withoutClocks().emit(T1_FIRST, null).emit(T1_SECOND, null).emit(T2_ONLY, null);
    }

    @Test
    void deliverable_returnsEarliestMatchingPendingMessagePerSender() {
        List<Pending> got = Delivery.deliverable(twoSenders(), CommunicationModel.P2P, T3, ANY);

        assertEquals(
                List.of(T1_FIRST, T2_ONLY),
                envelopes(got),
                "one candidate per sender: T1's first send and T2's only send");
    }

    @Test
    void deliverable_laterMatchingMessageFromSameSenderIsBlockedByEarlier() {
        Buffers before = twoSenders();
        assertTrue(
                envelopes(Delivery.deliverable(before, CommunicationModel.P2P, T3, ANY))
                        .contains(T1_FIRST));
        assertFalse(
                envelopes(Delivery.deliverable(before, CommunicationModel.P2P, T3, ANY))
                        .contains(T1_SECOND),
                "T1's second send is blocked while its first is pending");

        // Once the earlier one is delivered, the later one becomes deliverable — and only then.
        Buffers after = before.remove(T1_FIRST);
        assertEquals(
                List.of(T1_SECOND, T2_ONLY),
                envelopes(Delivery.deliverable(after, CommunicationModel.P2P, T3, ANY)));

        // What blocks is an earlier *matching* message, not an earlier one: with a guard that
        // accepts only Msg(2), the pending Msg(1) does not match, so it stops blocking and Msg(2)
        // becomes deliverable while Msg(1) is still on the channel.
        assertEquals(
                List.of(T1_SECOND),
                envelopes(Delivery.deliverable(before, CommunicationModel.P2P, T3, msgIs(2))),
                "with a guard that rejects Msg(1) the block lifts — that is the mval restriction");
    }

    @Test
    void deliverable_earlierNonMatchingMessageIsSkippedAndStaysPending() {
        Buffers b = twoSenders();

        // g accepts only Msg(2): T1's earlier Msg(1) does not match, so it does not block.
        List<Pending> got = Delivery.deliverable(b, CommunicationModel.P2P, T3, msgIs(2));
        assertEquals(List.of(T1_SECOND), envelopes(got));

        // ...and it stays pending: deliverable is a query, not a consumption.
        assertEquals(
                List.of(T1_FIRST, T1_SECOND),
                envelopes(b.pendingP2p(T1, T3)),
                "the skipped message is still on the channel, in emission order");
        assertEquals(3, b.size());

        // After delivering Msg(2) out of order, Msg(1) is the earliest matching pending again.
        Buffers after = b.remove(T1_SECOND);
        assertEquals(
                List.of(T1_FIRST, T2_ONLY),
                envelopes(Delivery.deliverable(after, CommunicationModel.P2P, T3, ANY)),
                "Must.pdf §2.3.3: selective receives let a node process messages against send order");
    }

    @Test
    void deliverable_messagesFromDifferentSendersAreIndependentlyDeliverable() {
        Buffers b = twoSenders();

        assertEquals(
                List.of(T1_FIRST, T2_ONLY),
                envelopes(Delivery.deliverable(b, CommunicationModel.P2P, T3, ANY)));

        // Consuming one sender's message leaves the other sender's answer unchanged.
        Buffers afterT2 = b.remove(T2_ONLY);
        assertEquals(
                List.of(T1_FIRST),
                envelopes(Delivery.deliverable(afterT2, CommunicationModel.P2P, T3, ANY)));

        Buffers afterT1 = b.remove(T1_FIRST);
        assertEquals(
                List.of(T1_SECOND, T2_ONLY),
                envelopes(Delivery.deliverable(afterT1, CommunicationModel.P2P, T3, ANY)),
                "p2p gives no cross-sender guarantee (Must §1.3): the channels are independent");
    }

    /**
     * Discriminating construction for this class. The per-sender rule is built the wrong way round
     * by hand — both messages given the <em>same</em> sender under {@code asyn}, and two messages
     * from <em>different</em> senders under {@code p2p} — to show that the single candidate in
     * {@code deliverable_laterMatchingMessageFromSameSenderIsBlockedByEarlier} comes from the
     * per-channel FIFO and not from {@code Delivery} returning at most one message.
     */
    @Test
    void deliverable_discriminatesPerSenderFifoFromAnAnswerSizeCap() {
        // Same two sends, asyn instead of p2p: both are deliverable, so the blocking above is
        // p2p's send order and not a cap on the answer size.
        Buffers asyn =
                Buffers.withoutClocks()
                        .emit(env(T1, 0, T3, CommunicationModel.ASYN, new Msg(1)), null)
                        .emit(env(T1, 1, T3, CommunicationModel.ASYN, new Msg(2)), null);
        assertEquals(2, Delivery.deliverable(asyn, CommunicationModel.ASYN, T3, ANY).size());

        // And a deliberately wrong p2p buffer in which T1's two messages are attributed to two
        // different senders: the rule then admits both, which is exactly the behaviour Def. 3.5
        // forbids for a single sender.
        Buffers miscredited =
                Buffers.withoutClocks()
                        .emit(env(T1, 0, T3, CommunicationModel.P2P, new Msg(1)), null)
                        .emit(env(T2, 1, T3, CommunicationModel.P2P, new Msg(2)), null);
        assertEquals(
                2,
                Delivery.deliverable(miscredited, CommunicationModel.P2P, T3, ANY).size(),
                "two senders, two candidates — the FIFO is per (sender, destination) channel");
    }
}
