package org.mpi_sws.jmc.mp;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.semantics.Pending;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared, deliberately boring fixtures for the {@code org.mpi_sws.jmc.mp} unit tests: a couple of
 * value-type messages, five process identifiers, an {@link Envelope} factory and a
 * "matches everything" guard.
 *
 * <p>This class holds <b>no static mutable state</b>. Every field is a {@code static final}
 * immutable value, so running several test methods of several test classes in one JVM cannot make
 * them interfere (plan/state/rose-state.md §S0.1; plan/state/DEVELOPER.md, "How you run tests").
 * The same discipline {@code mp.semantics} itself is held to (plan/state/mp-contract.md §5) is
 * applied here on purpose.
 */
public final class MpTestSupport {

    private MpTestSupport() {}

    /**
     * A plain integer-valued message — the smallest thing satisfying the value-type contract of
     * {@link MpMessage} (immutable, {@code equals} by value, no identity-dependent behaviour).
     *
     * @param n the payload
     */
    public record Msg(int n) implements MpMessage {}

    /**
     * An integer message carrying a tag, used where a test needs two distinguishable message
     * families in one buffer (for example a monitor notification versus a data message).
     *
     * @param tag the message family
     * @param n the payload
     */
    public record Tagged(String tag, int n) implements MpMessage {}

    /** Process 1. */
    public static final Tid T1 = Tid.of(1);

    /** Process 2. */
    public static final Tid T2 = Tid.of(2);

    /** Process 3. */
    public static final Tid T3 = Tid.of(3);

    /** Process 4. */
    public static final Tid T4 = Tid.of(4);

    /** Process 5, used as the monitor {@code Tm} in the Must-paper example tests. */
    public static final Tid T5 = Tid.of(5);

    /** The guard of an unselective receive: every message matches. */
    public static final Predicate<MpMessage> ANY = m -> true;

    /**
     * Builds an envelope.
     *
     * @param src the sending process
     * @param idx the 0-based index of this send among {@code src}'s sends
     * @param dst the destination process
     * @param m the communication model
     * @param v the message
     * @return the envelope
     */
    public static Envelope env(Tid src, long idx, Tid dst, CommunicationModel m, MpMessage v) {
        return new Envelope(src, idx, dst, m, v);
    }

    /**
     * A selective-receive guard accepting exactly the {@link Msg} carrying {@code n}.
     *
     * @param n the payload to accept
     * @return the guard
     */
    public static Predicate<MpMessage> msgIs(int n) {
        return m -> m instanceof Msg msg && msg.n() == n;
    }

    /**
     * A selective-receive guard accepting exactly the {@link Tagged} messages carrying
     * {@code tag}.
     *
     * @param tag the tag to accept
     * @return the guard
     */
    public static Predicate<MpMessage> taggedWith(String tag) {
        return m -> m instanceof Tagged t && t.tag().equals(tag);
    }

    /**
     * Projects a delivery answer onto the envelopes it names, so that a test can compare it with
     * {@link #env} values directly instead of reconstructing stamps.
     *
     * @param pending the pending messages, typically a {@code Delivery.deliverable} result
     * @return their envelopes, in the same order
     */
    public static List<Envelope> envelopes(List<Pending> pending) {
        List<Envelope> out = new ArrayList<>(pending.size());
        for (Pending p : pending) {
            out.add(p.envelope());
        }
        return out;
    }
}
