package org.mpi_sws.jmc.mp.graph;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * One event of an execution graph: its identity {@code ⟨t, i⟩}, its label, and — for a receive —
 * the predicate {@code vals(r)} it was executed with.
 *
 * <p>The predicate is the one place a graph carries behaviour rather than data. It is what the
 * consistency predicates evaluate {@code mval} with ({@code val(s) ∈ vals(r)}, Must Definition
 * 3.3 and Defs. 3.5–3.7). For a state-dependent guard it must be the predicate <em>as it stood at
 * this receive</em>, i.e. closed over the node's state at that moment — that is what "the set of
 * values matching the receive predicate" means in the paper. It is <b>not</b> part of the event's
 * identity: {@link #equals} and {@link #hashCode} use the key and the label only, and
 * {@link GraphCanon} never sees it (see {@link MpLabel}).
 */
public final class GraphEvent {

    /** The event's identity {@code ⟨t, i⟩}. */
    private final EventKey key;

    /** The event's label. */
    private final MpLabel label;

    /** {@code vals(r)} for a receive, closed over the node's state at that receive; else null. */
    private final Predicate<MpMessage> guard;

    private GraphEvent(EventKey key, MpLabel label, Predicate<MpMessage> guard) {
        this.key = Objects.requireNonNull(key, "key");
        this.label = Objects.requireNonNull(label, "label");
        this.guard = guard;
    }

    /**
     * Creates a non-receive event.
     *
     * @param key the identity
     * @param label the label; must not be a {@link MpLabel.Recv}
     * @return the event
     * @throws IllegalArgumentException if the label is a receive (use {@link #receive})
     */
    public static GraphEvent of(EventKey key, MpLabel label) {
        if (label instanceof MpLabel.Recv) {
            throw new IllegalArgumentException("a receive needs its predicate: use receive(...)");
        }
        return new GraphEvent(key, label, null);
    }

    /**
     * Creates a receive event with the predicate it was executed with.
     *
     * @param key the identity
     * @param label the receive label
     * @param guard {@code vals(r)} as a predicate, closed over the node's state at this receive
     * @return the event
     */
    public static GraphEvent receive(EventKey key, MpLabel.Recv label, Predicate<MpMessage> guard) {
        return new GraphEvent(key, label, Objects.requireNonNull(guard, "guard"));
    }

    /**
     * Returns the identity.
     *
     * @return the key
     */
    public EventKey key() {
        return key;
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public MpLabel label() {
        return label;
    }

    /**
     * Whether a value is in {@code vals(r)} of this receive.
     *
     * @param value a sent value
     * @return {@code true} iff the receive's predicate accepts it
     * @throws IllegalStateException if this is not a receive
     */
    public boolean accepts(MpMessage value) {
        if (guard == null) {
            throw new IllegalStateException(key + " is not a receive");
        }
        return guard.test(value);
    }

    /**
     * Whether this is a receive.
     *
     * @return {@code true} iff the label is a receive
     */
    public boolean isRecv() {
        return label instanceof MpLabel.Recv;
    }

    /**
     * Whether this is a send.
     *
     * @return {@code true} iff the label is a send
     */
    public boolean isSend() {
        return label instanceof MpLabel.Send;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GraphEvent e && key.equals(e.key) && label.equals(e.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, label);
    }

    @Override
    public String toString() {
        return key + ":" + label;
    }
}
