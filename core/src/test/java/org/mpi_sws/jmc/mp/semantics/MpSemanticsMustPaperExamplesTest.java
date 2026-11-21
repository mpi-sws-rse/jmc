package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;
import static org.mpi_sws.jmc.mp.MpTestSupport.T1;
import static org.mpi_sws.jmc.mp.MpTestSupport.T2;
import static org.mpi_sws.jmc.mp.MpTestSupport.T3;
import static org.mpi_sws.jmc.mp.MpTestSupport.T5;
import static org.mpi_sws.jmc.mp.MpTestSupport.env;
import static org.mpi_sws.jmc.mp.MpTestSupport.envelopes;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.MpTestSupport.Tagged;
import org.mpi_sws.jmc.mp.consistency.TaskClocks;
import org.mpi_sws.jmc.mp.consistency.VectorClock;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;

import java.util.List;

/**
 * The two Must-paper consistency examples, replayed through {@link Buffers}, {@link Delivery} and
 * {@link TaskClocks} — the delivery rule and the clock algebra together.
 *
 * <p>Pins the whole {@code CD} row of the plan/state/state.md §5.2 table against the source of
 * truth rather than against a hand-built clock:
 *
 * <ul>
 *   <li>plan/state/must-algorithm.md §1.3, closing paragraph (Must.pdf Example 3.1): with
 *       {@code T1: send(T3,1); send(T2,2)}, {@code T2: recv(); send(T3,3)}, {@code T3: recv();
 *       recv()} there are <b>two</b> {@code p2p}-consistent graphs and <b>one</b> {@code cd}-
 *       consistent one, because the two sends to {@code T3} are {@code porf}-connected through
 *       {@code T2}'s receive;</li>
 *   <li>Must.pdf Example 2.12 (plan/state/must-algorithm.md §1.6, "Monitors, temporal properties,
 *       and cd"): two {@code cd} notifications to a monitor, made causally related through a
 *       {@code p2p} message, must reach the monitor in that order — the case review round 1, M1
 *       records as the reason clocks travel on messages of <em>every</em> model.</li>
 * </ul>
 *
 * <p>Every stamp here is produced by {@link TaskClocks#emit}, {@link TaskClocks#deliver},
 * {@link TaskClocks#spawn} and {@link TaskClocks#join}, exactly as a backend would produce it —
 * never by hand — <em>except</em> in the {@code …_discriminates…} case at the end.
 */
// config/checkstyle/checkstyle.xml (SuppressionXpathSingleFilter, ~line 387) already exempts @Test
// methods whose names are underscore-separated lowerCamel segments, so no other class in this suite
// needs an annotation. All four names here have a *digit-led* segment ("3Example", "12") and fall
// outside that exemption; they are fixed by plan/state/mp-contract.md §6.1, which names the paper
// sections they replay. Measured: removing this annotation adds exactly these four MethodName
// warnings and no others (checkstyleTest --rerun-tasks, 768 vs 764).
@SuppressWarnings("checkstyle:MethodName")
public class MpSemanticsMustPaperExamplesTest {

    // Clock mode: withClocks() throughout, including the p2p half of the §1.3 example. That half
    // has no cd send of its own and could run withoutClocks(), but the whole claim is that the
    // *same* program under two models admits two graphs and one; building the two variants in
    // different modes would leave the mode as a second difference. Enabling clocks costs a
    // p2p-only program nothing (state.md §5.2: a backend may simply always enable them).

    /**
     * The state of the §1.3 example after every send has been emitted and {@code T2}'s receive has
     * happened, under one communication model.
     *
     * @param buffers the pending messages
     * @param toT3FromT1 {@code T1}'s send to {@code T3}
     * @param toT3FromT2 {@code T2}'s send to {@code T3}
     */
    private record Section13(Buffers buffers, Envelope toT3FromT1, Envelope toT3FromT2) {}

    /**
     * Builds the §1.3 example under {@code model}. T1 sends to T3 and then to T2; T2 receives and
     * sends to T3. Clocks are driven by {@link TaskClocks} throughout, so the stamps are whatever
     * the algebra says they are.
     */
    private static Section13 section13(CommunicationModel model) {
        TaskClocks clocks = TaskClocks.EMPTY;

        // T1: send(T3, 1)
        TaskClocks.Emission s1 = clocks.emit(T1.raw());
        Envelope toT3FromT1 = env(T1, 0, T3, model, new Msg(1));
        Buffers b = Buffers.withClocks().emit(toT3FromT1, s1.stamp());
        clocks = s1.clocks();

        // T1: send(T2, 2)
        TaskClocks.Emission s2 = clocks.emit(T1.raw());
        Envelope toT2 = env(T1, 1, T2, model, new Msg(2));
        b = b.emit(toT2, s2.stamp());
        clocks = s2.clocks();

        // T2: recv()
        assertEquals(
                List.of(toT2),
                envelopes(Delivery.deliverable(b, model, T2, ANY)),
                "T2 has exactly one thing to read under " + model);
        b = b.remove(toT2);
        clocks = clocks.deliver(T2.raw(), s2.stamp());

        // T2: send(T3, 3)
        TaskClocks.Emission s3 = clocks.emit(T2.raw());
        Envelope toT3FromT2 = env(T2, 0, T3, model, new Msg(3));
        b = b.emit(toT3FromT2, s3.stamp());

        return new Section13(b, toT3FromT1, toT3FromT2);
    }

    @Test
    void section1_3Example_p2pAdmitsBothReceiptOrders() {
        Section13 g = section13(CommunicationModel.P2P);

        // T3's first receive may read either send: two senders, two independent channels.
        assertEquals(
                List.of(g.toT3FromT1(), g.toT3FromT2()),
                envelopes(Delivery.deliverable(g.buffers(), CommunicationModel.P2P, T3, ANY)),
                "two p2p-consistent graphs: T3's receives read the two sends in either order");

        // Both orders complete: whichever T3 takes first, the other is then its second receive.
        Buffers t1First = g.buffers().remove(g.toT3FromT1());
        assertEquals(
                List.of(g.toT3FromT2()),
                envelopes(Delivery.deliverable(t1First, CommunicationModel.P2P, T3, ANY)));
        assertTrue(t1First.remove(g.toT3FromT2()).isEmpty());

        Buffers t2First = g.buffers().remove(g.toT3FromT2());
        assertEquals(
                List.of(g.toT3FromT1()),
                envelopes(Delivery.deliverable(t2First, CommunicationModel.P2P, T3, ANY)));
        assertTrue(t2First.remove(g.toT3FromT1()).isEmpty());
    }

    @Test
    void section1_3Example_cdAdmitsOnlyTheCausalOrder() {
        Section13 g = section13(CommunicationModel.CD);

        // The "opposite-order" graph is inconsistent under cd: the two sends to T3 are
        // porf-connected through T2's receive, so only T1's send is deliverable first.
        assertEquals(
                List.of(g.toT3FromT1()),
                envelopes(Delivery.deliverable(g.buffers(), CommunicationModel.CD, T3, ANY)),
                "one cd-consistent graph, against p2p's two");

        Buffers after = g.buffers().remove(g.toT3FromT1());
        assertEquals(
                List.of(g.toT3FromT2()),
                envelopes(Delivery.deliverable(after, CommunicationModel.CD, T3, ANY)));
        assertTrue(after.remove(g.toT3FromT2()).isEmpty());

        // cd is strictly stronger than p2p on this very program.
        assertEquals(
                2,
                Delivery.deliverable(
                                section13(CommunicationModel.P2P).buffers(),
                                CommunicationModel.P2P,
                                T3,
                                ANY)
                        .size());
        assertEquals(
                1, Delivery.deliverable(g.buffers(), CommunicationModel.CD, T3, ANY).size());
    }

    /**
     * Must.pdf Example 2.12. {@code T1: send_not(T3, 1, Tm); send^p2p(T2, 1)},
     * {@code T2: recv^p2p(); send_not(T3, 2, Tm)}, with the monitor {@code Tm} receiving under
     * {@code cd}. The notification sends are {@code send^cd(Tm, Send(...))} emitted just before
     * each monitored send (must-algorithm.md §1.6). The claim: after T2's receive and its
     * notification, {@code Tm} has exactly one deliverable message and it is T1's — the monitor
     * sees exactly one order and therefore cannot report a spurious violation.
     */
    @Test
    void example2_12_cdNotificationsThroughP2pAreOrdered() {
        TaskClocks clocks = TaskClocks.EMPTY;

        // T1: send_not(T3, 1, Tm) = send^cd(Tm, Send(T1,T3,1)); send^p2p(T3, 1)
        TaskClocks.Emission n1 = clocks.emit(T1.raw());
        Envelope notify1 = env(T1, 0, T5, CommunicationModel.CD, new Tagged("send-T1", 1));
        Buffers b = Buffers.withClocks().emit(notify1, n1.stamp());
        clocks = n1.clocks();

        TaskClocks.Emission d1 = clocks.emit(T1.raw());
        Envelope dataToT3FromT1 = env(T1, 1, T3, CommunicationModel.P2P, new Msg(1));
        b = b.emit(dataToT3FromT1, d1.stamp());
        clocks = d1.clocks();

        // T1: send^p2p(T2, 1) — the message that carries causality to T2.
        TaskClocks.Emission link = clocks.emit(T1.raw());
        Envelope toT2 = env(T1, 2, T2, CommunicationModel.P2P, new Msg(1));
        b = b.emit(toT2, link.stamp());
        clocks = link.clocks();

        // T2: recv^p2p()
        assertEquals(List.of(toT2), envelopes(Delivery.deliverable(b, CommunicationModel.P2P, T2, ANY)));
        b = b.remove(toT2);
        clocks = clocks.deliver(T2.raw(), link.stamp());

        // T2: send_not(T3, 2, Tm) = send^cd(Tm, Send(T2,T3,2)); send^p2p(T3, 2)
        TaskClocks.Emission n2 = clocks.emit(T2.raw());
        Envelope notify2 = env(T2, 0, T5, CommunicationModel.CD, new Tagged("send-T2", 2));
        b = b.emit(notify2, n2.stamp());
        clocks = n2.clocks();

        TaskClocks.Emission d2 = clocks.emit(T2.raw());
        b = b.emit(env(T2, 1, T3, CommunicationModel.P2P, new Msg(2)), d2.stamp());

        // The monitor sees exactly one order.
        assertTrue(n1.stamp().lessThan(n2.stamp()), "porf runs T1 -> (p2p) -> T2 between the two");
        assertEquals(
                List.of(notify1),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T5, ANY)),
                "exactly one deliverable notification, and it is T1's");

        Buffers afterFirst = b.remove(notify1);
        assertEquals(
                List.of(notify2),
                envelopes(Delivery.deliverable(afterFirst, CommunicationModel.CD, T5, ANY)),
                "and then T2's — a single monitor order, so no spurious violation is reportable");

        // Meanwhile the two data messages to T3 are under p2p, from two senders: both readable.
        assertEquals(
                2,
                Delivery.deliverable(b, CommunicationModel.P2P, T3, ANY).size(),
                "T3 may still read either sender — cd constrains the monitor, not the data path");
        assertEquals(List.of(dataToT3FromT1), envelopes(b.pendingP2p(T1, T3)));
    }

    /**
     * Discriminating construction for this class, and the concrete form of review round 1, M1.
     * Example 2.12 is rebuilt with the notification stamps a design that carries clocks
     * <em>only on {@code cd} messages</em> would produce — built by hand, bypassing
     * {@link TaskClocks#deliver}, because the algebra as written cannot produce them — and the
     * monitor then has two deliverable notifications, i.e. it sees both orders and can report a
     * violation that no run exhibits.
     */
    @Test
    void example2_12_discriminatesCrossModelClocks_monitorWouldSeeBothOrders() {
        // What the correct algebra gives (see the test above): {1:1} and {1:3, 2:1}.
        // What cd-only clocks give: T2 never joined anything, because it read a p2p message.
        VectorClock t1Stamp = VectorClock.EMPTY.tick(T1.raw());
        VectorClock t2StampCdOnly = VectorClock.EMPTY.tick(T2.raw());

        Envelope notify1 = env(T1, 0, T5, CommunicationModel.CD, new Tagged("send-T1", 1));
        Envelope notify2 = env(T2, 0, T5, CommunicationModel.CD, new Tagged("send-T2", 2));
        Buffers wrong = Buffers.withClocks().emit(notify1, t1Stamp).emit(notify2, t2StampCdOnly);

        assertTrue(
                t1Stamp.concurrentWith(t2StampCdOnly),
                "with cd-only clocks the two notifications look causally unrelated");
        assertEquals(
                List.of(notify1, notify2),
                envelopes(Delivery.deliverable(wrong, CommunicationModel.CD, T5, ANY)),
                "the monitor would see both orders — the spurious-violation defect of round 1, M1");

        // Control: with the stamp the real algebra produces, only T1's notification is deliverable.
        VectorClock t2StampCrossModel = t1Stamp.tick(T1.raw()).tick(T1.raw()).tick(T2.raw());
        Buffers right = Buffers.withClocks().emit(notify1, t1Stamp).emit(notify2, t2StampCrossModel);
        assertEquals(
                List.of(notify1),
                envelopes(Delivery.deliverable(right, CommunicationModel.CD, T5, ANY)));
    }
}
