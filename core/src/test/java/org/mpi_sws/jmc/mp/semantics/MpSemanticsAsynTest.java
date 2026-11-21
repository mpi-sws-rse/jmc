package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.mpi_sws.jmc.mp.events.MpMessage;

import java.util.List;
import java.util.function.Predicate;

/**
 * Pins the {@code ASYN} row of the delivery-rule table in plan/state/state.md §5.2:
 * "{@code m ∈ Bag[i]} and {@code g(m)}" — every guard-matching pending message is deliverable and
 * nothing else constrains the choice.
 *
 * <p>Must paper reference: {@code consistent_asyn(G)} holds iff {@code G} is well-formed
 * (Definition 3.4); there is no send order, so the operational rule is the {@code mval} guard
 * alone. Also pins the §5.2 bullet "<b>Selective receive = guards</b>": the guard filters
 * <em>before</em> the choice.
 */
public class MpSemanticsAsynTest {

    // Clock mode: withoutClocks(). The asyn rule reads no stamp, and a program with only asyn
    // sends declares no cd, so this is the mode a backend would build here; it also makes the
    // unstamped emissions below the *correct* calls rather than ones Buffers.emit tolerates.

    private static final Envelope A0 = env(T1, 0, T3, CommunicationModel.ASYN, new Msg(1));
    private static final Envelope A1 = env(T1, 1, T3, CommunicationModel.ASYN, new Msg(2));
    private static final Envelope A2 = env(T2, 0, T3, CommunicationModel.ASYN, new Msg(3));

    private static Buffers threePending() {
        return Buffers.withoutClocks().emit(A0, null).emit(A1, null).emit(A2, null);
    }

    @Test
    void deliverable_returnsAllGuardMatchingPendingMessages() {
        Buffers b = threePending();

        List<Pending> got = Delivery.deliverable(b, CommunicationModel.ASYN, T3, ANY);

        assertEquals(
                List.of(A0, A1, A2),
                envelopes(got),
                "asyn imposes no order: all three pending messages are deliverable, "
                        + "in Pending.CANONICAL order (sender, then send index)");
        assertEquals(3, b.size(), "deliverable must not consume anything");
    }

    @Test
    void deliverable_excludesGuardNonMatchingMessages() {
        Buffers b = threePending();

        // g(m) ::= m is Msg(2) — a selective receive.
        assertEquals(
                List.of(A1), envelopes(Delivery.deliverable(b, CommunicationModel.ASYN, T3, msgIs(2))));

        // A guard nothing matches yields the empty set, not "everything".
        assertEquals(
                List.of(),
                envelopes(Delivery.deliverable(b, CommunicationModel.ASYN, T3, msgIs(99))),
                "the guard filters before the choice (state.md §5.2, 'selective receive = guards')");

        // A guard matching two of the three yields exactly those two.
        Predicate<MpMessage> odd = m -> m instanceof Msg msg && msg.n() % 2 == 1;
        assertEquals(
                List.of(A0, A2), envelopes(Delivery.deliverable(b, CommunicationModel.ASYN, T3, odd)));

        assertEquals(3, b.size(), "the non-matching messages stay pending");
    }

    /**
     * Discriminating construction for this class. "Returns all matching pending messages" would
     * also be satisfied by a rule that ignores the buffer structure entirely, so this pins the two
     * ways the answer <em>is</em> restricted under {@code asyn}: by destination, and — for the same
     * message sequence under a different model — by that model's send order.
     */
    @Test
    void deliverable_discriminatesAsynFromDestinationAndFromP2p() {
        Buffers b = threePending().emit(env(T1, 2, T4, CommunicationModel.ASYN, new Msg(1)), null);

        // Destination is a real restriction: T4's message never appears in T3's answer.
        assertEquals(3, Delivery.deliverable(b, CommunicationModel.ASYN, T3, ANY).size());
        assertEquals(1, Delivery.deliverable(b, CommunicationModel.ASYN, T4, ANY).size());

        // The very same three messages under p2p yield strictly fewer candidates, because T1's
        // second send is then blocked by its first. So "all three" above is a property of asyn,
        // not of Delivery returning whatever is pending.
        Buffers p2p =
                Buffers.withoutClocks()
                        .emit(env(T1, 0, T3, CommunicationModel.P2P, new Msg(1)), null)
                        .emit(env(T1, 1, T3, CommunicationModel.P2P, new Msg(2)), null)
                        .emit(env(T2, 0, T3, CommunicationModel.P2P, new Msg(3)), null);
        List<Pending> underP2p = Delivery.deliverable(p2p, CommunicationModel.P2P, T3, ANY);
        assertEquals(2, underP2p.size(), "p2p admits one message per sender");
        assertTrue(
                underP2p.size() < Delivery.deliverable(b, CommunicationModel.ASYN, T3, ANY).size(),
                "asyn is strictly weaker than p2p on the same message sequence");
    }
}
