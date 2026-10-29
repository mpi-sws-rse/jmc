package org.mpi_sws.jmc.mp.semantics;

import org.mpi_sws.jmc.mp.consistency.VectorClock;
import org.mpi_sws.jmc.mp.events.Envelope;

import java.util.Comparator;
import java.util.Objects;

/**
 * A message that has been emitted into a buffer and not yet delivered — the operational analogue
 * of a Must <em>unread send</em> ({@code US}) at the moment of delivery. The stamp below is the
 * clock the send had when it was emitted, which is what causal delivery compares.
 *
 * <p>The stamp is the sender's vector clock at emission ({@link
 * org.mpi_sws.jmc.mp.consistency.TaskClocks#emit}). It is present on messages of <em>every</em>
 * model whenever the program uses {@code cd} anywhere, because {@code porf} crosses models (see
 * Example 2.12 of the Must paper); it is {@code null} only when the program uses no
 * {@code cd} at all and clocks are disabled. A {@code cd} message must always carry one
 * ({@link Buffers#emit}).
 *
 * @param envelope the message and the identity of its send
 * @param clock the sender's clock at emission, or {@code null} when clocks are disabled
 */
public record Pending(Envelope envelope, VectorClock clock) {

    /** Canonical order: that of {@link Envelope#CANONICAL}. */
    public static final Comparator<Pending> CANONICAL =
            Comparator.comparing(Pending::envelope, Envelope.CANONICAL);

    /** Validates that the envelope is present. */
    public Pending {
        Objects.requireNonNull(envelope, "envelope");
    }

    /**
     * Whether this message must be delivered before another under causal delivery, i.e. its
     * stamp is strictly below the other's — which, by the clock lemma, means its send is
     * {@code porf}-before the other's.
     *
     * @param other another pending message
     * @return {@code true} iff {@code this.clock < other.clock}
     * @throws IllegalStateException if either message carries no stamp
     */
    public boolean causallyBefore(Pending other) {
        if (clock == null || other.clock == null) {
            throw new IllegalStateException("causal comparison requires stamped messages");
        }
        return clock.lessThan(other.clock);
    }
}
