package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mpi_sws.jmc.mp.consistency.TaskClocks;
import org.mpi_sws.jmc.mp.consistency.VectorClock;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;

import java.util.List;

/**
 * Pins the {@code CD} row of the delivery-rule table in plan/state/state.md §5.2: "{@code g(m)} and
 * there is <b>no</b> pending {@code m'} to {@code i} with {@code g(m')} and
 * {@code VC(m') < VC(m)} — {@code m} is {@code porf}-minimal among matching pending {@code cd} messages to
 * {@code i}", together with the four bullets of the "<b>CD and vector clocks — the clock algebra</b>"
 * item of the same section (emit ticks; deliver joins; spawn propagates; join absorbs).
 *
 * <p>Must paper reference: {@code consistent_cd}, Definition 3.6, with
 * {@code so ≜ [S^cd]; porf; [S^cd]}.
 *
 * <p>Every stamp in this class is produced by {@link TaskClocks} operations, exactly as a backend
 * would produce it — never by hand — <em>except</em> in the {@code …_discriminates…} case, which
 * constructs the wrong stamps deliberately.
 */
public class MpSemanticsCdTest {

    // Clock mode: withClocks() throughout. Every program here uses cd, so the backend enables
    // clocks, and then *every* emission of *every* model must carry a stamp — including the p2p
    // carrier messages below, which is the whole point of review round 1, M1.

    @Test
    void deliverable_excludesMessageDominatedByCausallyEarlierMatchingPending() {
        // T1: send^cd(T3, 1); send^p2p(T2, 9)      T2: recv^p2p(); send^cd(T3, 2)
        // The porf path between the two cd sends runs through a p2p reads-from edge.
        TaskClocks clocks = TaskClocks.EMPTY;

        TaskClocks.Emission n1 = clocks.emit(T1.raw());
        Envelope first = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));
        Buffers b = Buffers.withClocks().emit(first, n1.stamp());
        clocks = n1.clocks();

        TaskClocks.Emission carrier = clocks.emit(T1.raw());
        Envelope link = env(T1, 1, T2, CommunicationModel.P2P, new Msg(9));
        b = b.emit(link, carrier.stamp());
        clocks = carrier.clocks();

        // T2 receives the p2p message: deliver joins its clock with the stamp.
        assertEquals(
                List.of(link),
                envelopes(Delivery.deliverable(b, CommunicationModel.P2P, T2, ANY)),
                "the carrier is the only thing pending for T2");
        b = b.remove(link);
        clocks = clocks.deliver(T2.raw(), carrier.stamp());

        TaskClocks.Emission n2 = clocks.emit(T2.raw());
        Envelope second = env(T2, 0, T3, CommunicationModel.CD, new Msg(2));
        b = b.emit(second, n2.stamp());

        assertTrue(n1.stamp().lessThan(n2.stamp()), "VC(first) < VC(second) by the clock lemma");
        assertEquals(
                List.of(first),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)),
                "the causally later cd message is excluded while the earlier one is pending");

        // Once the dominating message is delivered, the dominated one becomes deliverable.
        assertEquals(
                List.of(second),
                envelopes(Delivery.deliverable(b.remove(first), CommunicationModel.CD, T3, ANY)));

        // The domination is 'among *matching* pending messages' (the ∩ mval of Def. 3.6): a guard
        // that rejects the earlier message lifts the constraint on the later one.
        assertEquals(
                List.of(second),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, msgIs(2))),
                "a non-matching causally earlier message imposes no constraint");
    }

    @Test
    void deliverable_causallyConcurrentMessagesAreBothDeliverable() {
        TaskClocks clocks = TaskClocks.EMPTY;

        TaskClocks.Emission a = clocks.emit(T1.raw());
        Envelope fromT1 = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));
        clocks = a.clocks();

        TaskClocks.Emission c = clocks.emit(T2.raw());
        Envelope fromT2 = env(T2, 0, T3, CommunicationModel.CD, new Msg(2));

        assertTrue(a.stamp().concurrentWith(c.stamp()), "no porf path between T1 and T2");

        Buffers b = Buffers.withClocks().emit(fromT1, a.stamp()).emit(fromT2, c.stamp());
        assertEquals(
                List.of(fromT1, fromT2),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)),
                "cd only orders causally related sends; concurrent ones are both candidates");
    }

    @Test
    void deliverable_sameSenderCdSendsAreDeliveredInOrder() {
        // The review round 2, M3 regression guard: the tick on emit is what orders these two.
        TaskClocks.Emission e1 = TaskClocks.EMPTY.emit(T1.raw());
        TaskClocks.Emission e2 = e1.clocks().emit(T1.raw());

        Envelope m1 = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));
        Envelope m2 = env(T1, 1, T3, CommunicationModel.CD, new Msg(2));
        Buffers b = Buffers.withClocks().emit(m1, e1.stamp()).emit(m2, e2.stamp());

        assertTrue(e1.stamp().lessThan(e2.stamp()));
        assertEquals(
                List.of(m1),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)),
                "two cd sends from one task to one destination are delivered in send order");
        assertEquals(
                List.of(m2),
                envelopes(Delivery.deliverable(b.remove(m1), CommunicationModel.CD, T3, ANY)));
    }

    @Test
    void causality_isEstablishedByReceive() {
        // T1 sends cd n1 to T3, then asyn x to T2; T2 receives x and then sends cd n2 to T3.
        TaskClocks clocks = TaskClocks.EMPTY;
        TaskClocks.Emission n1 = clocks.emit(T1.raw());
        clocks = n1.clocks();
        TaskClocks.Emission x = clocks.emit(T1.raw());
        clocks = x.clocks();

        Envelope first = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));
        Envelope carrier = env(T1, 1, T2, CommunicationModel.ASYN, new Msg(9));

        TaskClocks withReceive = clocks.deliver(T2.raw(), x.stamp());
        TaskClocks.Emission n2 = withReceive.emit(T2.raw());
        Envelope second = env(T2, 0, T3, CommunicationModel.CD, new Msg(2));

        Buffers b = Buffers.withClocks().emit(first, n1.stamp()).emit(carrier, x.stamp());
        assertEquals(
                List.of(first),
                envelopes(Delivery.deliverable(b.emit(second, n2.stamp()), CommunicationModel.CD, T3, ANY)),
                "the receive made T1's earlier cd send porf-before T2's cd send");

        // Failing direction: without the deliver join, T2's clock never sees T1, and the two cd
        // sends become concurrent — both deliverable.
        TaskClocks.Emission n2NoJoin = clocks.emit(T2.raw());
        assertTrue(n1.stamp().concurrentWith(n2NoJoin.stamp()));
        assertEquals(
                2,
                Delivery.deliverable(b.emit(second, n2NoJoin.stamp()), CommunicationModel.CD, T3, ANY)
                        .size(),
                "omitting the join on deliver admits a delivery order Must forbids");
    }

    @Test
    void causality_isEstablishedBySpawn() {
        // rose-state.md F7(b): the child must start from the parent's clock at the spawn.
        TaskClocks.Emission parentSend = TaskClocks.EMPTY.emit(T1.raw());
        Envelope fromParent = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));

        TaskClocks spawned = parentSend.clocks().spawn(T1.raw(), T4.raw());
        TaskClocks.Emission childSend = spawned.emit(T4.raw());
        Envelope fromChild = env(T4, 0, T3, CommunicationModel.CD, new Msg(2));

        Buffers b =
                Buffers.withClocks()
                        .emit(fromParent, parentSend.stamp())
                        .emit(fromChild, childSend.stamp());

        assertTrue(parentSend.stamp().lessThan(childSend.stamp()));
        assertEquals(
                List.of(fromParent),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)),
                "everything the parent did before the spawn is porf-before what the child does");

        // Failing direction: no spawn propagation -> concurrent -> both deliverable (F7(b)).
        TaskClocks.Emission childNoSpawn = parentSend.clocks().emit(T4.raw());
        Buffers wrong =
                Buffers.withClocks()
                        .emit(fromParent, parentSend.stamp())
                        .emit(fromChild, childNoSpawn.stamp());
        assertEquals(
                2,
                Delivery.deliverable(wrong, CommunicationModel.CD, T3, ANY).size(),
                "omitting spawn propagation is a missed-behaviour error");
    }

    @Test
    void causality_isEstablishedByJoin() {
        // T2 sends cd m1 to T3 and terminates; T1 joins T2 and then sends cd m2 to T3.
        TaskClocks.Emission fromJoined = TaskClocks.EMPTY.emit(T2.raw());
        Envelope m1 = env(T2, 0, T3, CommunicationModel.CD, new Msg(1));

        TaskClocks afterJoin = fromJoined.clocks().join(T1.raw(), T2.raw());
        TaskClocks.Emission fromJoiner = afterJoin.emit(T1.raw());
        Envelope m2 = env(T1, 0, T3, CommunicationModel.CD, new Msg(2));

        Buffers b = Buffers.withClocks().emit(m1, fromJoined.stamp()).emit(m2, fromJoiner.stamp());

        assertTrue(fromJoined.stamp().lessThan(fromJoiner.stamp()));
        assertEquals(
                List.of(m1),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)),
                "the join makes the joined task's send porf-before the joiner's later send");

        // Failing direction: without the join, the two sends are concurrent.
        TaskClocks.Emission noJoin = fromJoined.clocks().emit(T1.raw());
        Buffers wrong = Buffers.withClocks().emit(m1, fromJoined.stamp()).emit(m2, noJoin.stamp());
        assertTrue(fromJoined.stamp().concurrentWith(noJoin.stamp()));
        assertEquals(2, Delivery.deliverable(wrong, CommunicationModel.CD, T3, ANY).size());
    }

    /**
     * A {@code cd} message never reaches a buffer unstamped, under either clock mode — and, since
     * amendment A4, neither does anything else in a program that uses {@code cd}. The two modes
     * reject it for different reasons, and both messages are pinned here so that a future change
     * that collapses the two into one silent path is visible.
     */
    @Test
    void emit_cdMessageWithoutStamp_throws() {
        Envelope cd = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));

        // Clocks enabled — the mode of every program in this class: an unstamped cd send is a
        // backend that forgot to stamp.
        IllegalArgumentException enabled =
                assertThrows(IllegalArgumentException.class, () -> Buffers.withClocks().emit(cd, null));
        assertTrue(
                enabled.getMessage().contains("clocks enabled"),
                "an unstamped cd send must fail loudly — was: " + enabled.getMessage());

        // Clocks disabled: a cd send cannot exist at all, stamped or not, because the model set
        // the backend derived the bit from said the program has no cd (state.md §5.2, "detection
        // of uses cd"). An engine error, never a silent degradation.
        IllegalArgumentException disabled =
                assertThrows(
                        IllegalArgumentException.class, () -> Buffers.withoutClocks().emit(cd, null));
        assertTrue(
                disabled.getMessage().contains("cd send"),
                "was: " + disabled.getMessage());

        // The other three models are *also* rejected unstamped once clocks are on. This is the
        // half round 01 left open: the M1 shape is an unstamped p2p send in a cd-using program,
        // and Buffers used to wave it through (review round 1, m4).
        for (CommunicationModel model :
                List.of(CommunicationModel.ASYN, CommunicationModel.P2P, CommunicationModel.MBOX)) {
            assertTrue(
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            Buffers.withClocks()
                                                    .emit(env(T1, 0, T3, model, new Msg(1)), null))
                            .getMessage()
                            .contains("clocks enabled"),
                    "an unstamped " + model + " send in a cd-using program must fail loudly");
        }

        // ...and are accepted unstamped in the mode that says the program has no cd at all.
        Buffers.withoutClocks().emit(env(T1, 0, T3, CommunicationModel.ASYN, new Msg(1)), null);
        Buffers.withoutClocks().emit(env(T1, 0, T3, CommunicationModel.P2P, new Msg(1)), null);
        Buffers.withoutClocks().emit(env(T1, 0, T3, CommunicationModel.MBOX, new Msg(1)), null);
    }

    /**
     * Discriminating construction for this class, and the direct form of review round 2, M3. The
     * two same-sender {@code cd} sends of
     * {@code deliverable_sameSenderCdSendsAreDeliveredInOrder} are rebuilt with <b>equal</b> stamps
     * — by hand, bypassing {@link TaskClocks}, which is what "stamp and join but never advance"
     * produces — and both are then deliverable. That is the delivery Must forbids, and it is what
     * makes the single-candidate assertion above a real one.
     */
    @Test
    void deliverable_discriminatesTheEmitTick_equalStampsMakeBothDeliverable() {
        VectorClock stampA = VectorClock.EMPTY.tick(T1.raw());
        VectorClock stampB = VectorClock.EMPTY.tick(T1.raw());

        Envelope m1 = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));
        Envelope m2 = env(T1, 1, T3, CommunicationModel.CD, new Msg(2));
        Buffers wrong = Buffers.withClocks().emit(m1, stampA).emit(m2, stampB);

        assertEquals(stampA, stampB, "the no-tick discipline stamps both sends identically");
        assertFalse(
                stampA.lessThan(stampB),
                "equal clocks give the cd rule nothing to order the two sends by");
        assertFalse(stampB.lessThan(stampA));
        assertEquals(
                List.of(m1, m2),
                envelopes(Delivery.deliverable(wrong, CommunicationModel.CD, T3, ANY)),
                "without the tick on emit both sends are deliverable and the destination may read "
                        + "the second first — review round 2, M3");
    }
}
