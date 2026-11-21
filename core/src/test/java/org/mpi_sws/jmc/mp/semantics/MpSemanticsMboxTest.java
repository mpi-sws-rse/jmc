package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;
import static org.mpi_sws.jmc.mp.MpTestSupport.T1;
import static org.mpi_sws.jmc.mp.MpTestSupport.T2;
import static org.mpi_sws.jmc.mp.MpTestSupport.T3;
import static org.mpi_sws.jmc.mp.MpTestSupport.T4;
import static org.mpi_sws.jmc.mp.MpTestSupport.env;
import static org.mpi_sws.jmc.mp.MpTestSupport.envelopes;
import static org.mpi_sws.jmc.mp.MpTestSupport.msgIs;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;

import java.util.List;

/**
 * Pins the {@code MBOX} row of the delivery-rule table in plan/state/state.md §5.2: the buffer is a
 * "FIFO list per destination, in global emission order — the relative order only", and "{@code m}
 * is deliverable iff {@code g(m)} and there is <b>no</b> pending {@code m'} to {@code i} earlier in
 * the list with {@code g(m')}" — earliest matching by global emission order.
 *
 * <p>Must paper reference: {@code consistent_mbox}, Definition 3.7, under the reading state.md §5.2
 * fixes in its "<b>MBOX adequacy — the witness is {@code porf}-consistent</b>" bullet: {@code so}
 * is a total order on the {@code mbox} sends containing {@code porf} restricted to them
 * (TraceForge {@code cons.rs}, {@code send_before}). Operationally that witness is the one global
 * emission order, which is why the answer is a single message and why it is <em>not</em> per
 * sender.
 */
public class MpSemanticsMboxTest {

    // Clock mode: withoutClocks(). The mbox rule reads the stored emission order, never a stamp,
    // and these programs contain no cd send — so clocks are off and every emission here is
    // correctly unstamped.

    // Emission order: T2's send, then T1's two sends. Note that this is NOT canonical order.
    private static final Envelope EMITTED_FIRST = env(T2, 0, T3, CommunicationModel.MBOX, new Msg(1));
    private static final Envelope EMITTED_SECOND = env(T1, 0, T3, CommunicationModel.MBOX, new Msg(2));
    private static final Envelope EMITTED_THIRD = env(T1, 1, T3, CommunicationModel.MBOX, new Msg(3));

    private static Buffers threeInGlobalOrder() {
        return Buffers.withoutClocks()
                .emit(EMITTED_FIRST, null)
                .emit(EMITTED_SECOND, null)
                .emit(EMITTED_THIRD, null);
    }

    @Test
    void deliverable_returnsEarliestMatchingByGlobalSendOrder() {
        Buffers b = threeInGlobalOrder();

        assertEquals(
                List.of(EMITTED_FIRST),
                envelopes(Delivery.deliverable(b, CommunicationModel.MBOX, T3, ANY)),
                "exactly one candidate: the earliest message in global emission order");

        // Draining the mailbox reproduces the emission order exactly, one message at a time.
        Buffers after = b.remove(EMITTED_FIRST);
        assertEquals(
                List.of(EMITTED_SECOND),
                envelopes(Delivery.deliverable(after, CommunicationModel.MBOX, T3, ANY)));
        after = after.remove(EMITTED_SECOND);
        assertEquals(
                List.of(EMITTED_THIRD),
                envelopes(Delivery.deliverable(after, CommunicationModel.MBOX, T3, ANY)));
        assertEquals(
                List.of(),
                envelopes(
                        Delivery.deliverable(
                                after.remove(EMITTED_THIRD), CommunicationModel.MBOX, T3, ANY)));
    }

    @Test
    void deliverable_orderIsGlobalNotPerSender() {
        Buffers b = threeInGlobalOrder();

        // T1's Msg(2) is that sender's earliest, but T2's Msg(1) was emitted before it globally,
        // so it is not a candidate. Under p2p it would be one — that is the whole difference.
        assertEquals(
                List.of(EMITTED_FIRST),
                envelopes(Delivery.deliverable(b, CommunicationModel.MBOX, T3, ANY)));

        Buffers sameSequenceUnderP2p =
                Buffers.withoutClocks()
                        .emit(env(T2, 0, T3, CommunicationModel.P2P, new Msg(1)), null)
                        .emit(env(T1, 0, T3, CommunicationModel.P2P, new Msg(2)), null)
                        .emit(env(T1, 1, T3, CommunicationModel.P2P, new Msg(3)), null);
        assertEquals(
                2,
                Delivery.deliverable(sameSequenceUnderP2p, CommunicationModel.P2P, T3, ANY).size(),
                "p2p gives one candidate per sender; mbox gives one in total");

        // The global order also couples different senders across a delivery: after T2's message
        // is taken, T1's first send is next, not T1's second.
        assertEquals(
                List.of(EMITTED_SECOND),
                envelopes(
                        Delivery.deliverable(
                                b.remove(EMITTED_FIRST), CommunicationModel.MBOX, T3, ANY)));
    }

    @Test
    void deliverable_nonMatchingEarlierMessageDoesNotBlock() {
        Buffers b = threeInGlobalOrder();

        // g accepts only Msg(2): the globally earlier Msg(1) does not match and is skipped.
        assertEquals(
                List.of(EMITTED_SECOND),
                envelopes(Delivery.deliverable(b, CommunicationModel.MBOX, T3, msgIs(2))),
                "the ∩ mval restriction: only matching earlier messages block");

        // ...and skipping does not consume: the earlier message is still pending.
        assertEquals(
                List.of(EMITTED_FIRST, EMITTED_SECOND, EMITTED_THIRD),
                envelopes(b.pending(CommunicationModel.MBOX, T3)),
                "the mailbox is unchanged, still in global emission order");

        // Skipping is transitive: with a guard matching only the third, both earlier ones skip.
        assertEquals(
                List.of(EMITTED_THIRD),
                envelopes(Delivery.deliverable(b, CommunicationModel.MBOX, T3, msgIs(3))));

        assertEquals(
                List.of(),
                envelopes(Delivery.deliverable(b, CommunicationModel.MBOX, T3, msgIs(99))));
    }

    /**
     * Discriminating construction for this class. "Exactly one candidate" would also hold for a
     * rule that simply returns the first thing it finds in any order, so this builds the
     * deliberately wrong buffer — the same three messages inserted in canonical (sender, index)
     * order instead of emission order — and shows the answer changes. It is the concrete form of
     * the state.md §5.2 warning that "an arbitrary total order would make {@code mbox} collapse to
     * {@code asyn}": what the answer means depends entirely on the stored order being the
     * emission order.
     */
    @Test
    void deliverable_discriminatesGlobalEmissionOrderFromCanonicalOrder() {
        Buffers emissionOrder = threeInGlobalOrder();
        Buffers canonicalOrder =
                Buffers.withoutClocks()
                        .emit(EMITTED_SECOND, null) // T1#0
                        .emit(EMITTED_THIRD, null) // T1#1
                        .emit(EMITTED_FIRST, null); // T2#0

        assertEquals(
                List.of(EMITTED_FIRST),
                envelopes(Delivery.deliverable(emissionOrder, CommunicationModel.MBOX, T3, ANY)));
        assertEquals(
                List.of(EMITTED_SECOND),
                envelopes(Delivery.deliverable(canonicalOrder, CommunicationModel.MBOX, T3, ANY)),
                "a different emission order gives a different answer — the rule really reads the "
                        + "stored order, and is not sorting by sender");

        // Destination is a real restriction too: a mailbox is per destination.
        Envelope elsewhere = env(T1, 2, T4, CommunicationModel.MBOX, new Msg(4));
        assertEquals(
                List.of(EMITTED_FIRST),
                envelopes(
                        Delivery.deliverable(
                                emissionOrder.emit(elsewhere, null),
                                CommunicationModel.MBOX,
                                T3,
                                ANY)));
        assertEquals(
                List.of(elsewhere),
                envelopes(
                        Delivery.deliverable(
                                emissionOrder.emit(elsewhere, null),
                                CommunicationModel.MBOX,
                                T4,
                                ANY)));
    }
}
