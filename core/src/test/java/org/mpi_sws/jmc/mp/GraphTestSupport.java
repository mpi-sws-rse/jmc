package org.mpi_sws.jmc.mp;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;

import java.util.function.Predicate;

/**
 * Fixture for the {@code org.mpi_sws.jmc.mp.graph} and {@code org.mpi_sws.jmc.mp.consistency}
 * graph tests: one factory per {@link MpLabel} shape, so a Must-paper graph reads as the program
 * that produced it.
 *
 * <p>Every factory takes the event's own thread as a {@link Tid} rather than a raw {@code long}:
 * a graph test names five threads a dozen times each, and {@code send(T1, 0, MBOX, T2, …)} is the
 * form in which a reviewer can check it against the paper. {@link #key(Tid, long)} builds the
 * matching {@link EventKey}, which is what {@link ExecutionGraph}'s queries and
 * {@code MpConsistency}'s answers speak in.
 *
 * <p>Messages come from {@link MpTestSupport} ({@link MpTestSupport.Msg},
 * {@link MpTestSupport#ANY}, {@link MpTestSupport#msgIs(int)}), and so do the {@link Tid}
 * constants, re-exported here only so that a graph test needs one static-import block rather than
 * two.
 *
 * <p>This class holds <b>no static mutable state</b>: every field is a {@code static final}
 * immutable value (plan/state/rose-state.md §S0.1; plan/state/DEVELOPER.md, "How you run tests").
 */
public final class GraphTestSupport {

    private GraphTestSupport() {}

    /** Process 1. */
    public static final Tid T1 = MpTestSupport.T1;

    /** Process 2. */
    public static final Tid T2 = MpTestSupport.T2;

    /** Process 3. */
    public static final Tid T3 = MpTestSupport.T3;

    /** Process 4. */
    public static final Tid T4 = MpTestSupport.T4;

    /** Process 5. */
    public static final Tid T5 = MpTestSupport.T5;

    /**
     * Builds an event identity {@code ⟨t, i⟩} (Must Definition 3.1).
     *
     * @param t the thread
     * @param idx the 0-based index of the event within that thread
     * @return the key
     */
    public static EventKey key(Tid t, long idx) {
        return new EventKey(t.raw(), idx);
    }

    /**
     * A send event {@code S^M(dst, value)}.
     *
     * @param t the sending thread
     * @param idx the event's index within {@code t}
     * @param model the communication model
     * @param dst the destination
     * @param value the message
     * @return the event
     */
    public static GraphEvent send(
            Tid t, long idx, CommunicationModel model, Tid dst, MpMessage value) {
        return GraphEvent.of(key(t, idx), new MpLabel.Send(model, dst, value));
    }

    /**
     * A receive event {@code R^{M,β}(vals)}.
     *
     * @param t the receiving thread
     * @param idx the event's index within {@code t}
     * @param model the communication model
     * @param blocking {@code true} for {@code β = b}, {@code false} for {@code β = nb}
     * @param guard {@code vals(r)} as a predicate
     * @return the event
     */
    public static GraphEvent recv(
            Tid t,
            long idx,
            CommunicationModel model,
            boolean blocking,
            Predicate<MpMessage> guard) {
        return GraphEvent.receive(key(t, idx), new MpLabel.Recv(model, blocking), guard);
    }

    /**
     * A blocking, unselective receive — {@code R^{M,b}(Val)}, the receive of every Must-paper
     * example that does not name a predicate.
     *
     * @param t the receiving thread
     * @param idx the event's index within {@code t}
     * @param model the communication model
     * @return the event
     */
    public static GraphEvent recv(Tid t, long idx, CommunicationModel model) {
        return recv(t, idx, model, true, MpTestSupport.ANY);
    }

    /**
     * A nondeterministic choice {@code ND^S(v)}.
     *
     * @param t the choosing thread
     * @param idx the event's index within {@code t}
     * @param count the number of options
     * @param chosen the 0-based index of the option taken
     * @return the event
     */
    public static GraphEvent nondet(Tid t, long idx, int count, int chosen) {
        return GraphEvent.of(key(t, idx), new MpLabel.Nondet(count, chosen));
    }

    /**
     * A block event {@code B}.
     *
     * @param t the blocked thread
     * @param idx the event's index within {@code t}
     * @return the event
     */
    public static GraphEvent block(Tid t, long idx) {
        return GraphEvent.of(key(t, idx), new MpLabel.Block());
    }
}
