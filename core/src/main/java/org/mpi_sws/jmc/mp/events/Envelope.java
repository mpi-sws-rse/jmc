package org.mpi_sws.jmc.mp.events;

import java.util.Comparator;
import java.util.Objects;

/**
 * A sent message — the payload of a Must send label {@code S^M(t, v)} (Definition 3.1 of the Must
 * paper) together with the identity of the send that produced it.
 *
 * <p>The pair {@code (src, sendIndex)} identifies the send across serialization and re-execution;
 * a backend's answer to "which message does this receive read" names a message by this pair. The
 * index is assigned by the backend, which counts the sends of each task per iteration; the
 * user-facing API never keeps it.
 *
 * <p>An envelope carries <em>no</em> clock and <em>no</em> mailbox position: causality and
 * mailbox order belong to the buffers that hold pending messages, not to the message itself.
 *
 * @param src the sending process
 * @param sendIndex the 0-based index of this send among {@code src}'s sends in this iteration
 * @param dst the destination process
 * @param model the communication model of this send
 * @param value the message
 */
public record Envelope(Tid src, long sendIndex, Tid dst, CommunicationModel model, MpMessage value) {

    /**
     * The canonical order of envelopes: by sender, then by send index. Every order-insensitive
     * buffer in the core is stored sorted by this comparator so that equal multisets compare
     * equal.
     *
     * <p>{@code (src, sendIndex)} identifies one send, so this is a total order on the envelopes
     * of any single per-destination, per-model buffer. It compares neither destination nor model
     * nor value, and is not a total order on a list that mixes destinations or models.
     */
    public static final Comparator<Envelope> CANONICAL =
            Comparator.comparing(Envelope::src).thenComparingLong(Envelope::sendIndex);

    /** Validates the components; {@code sendIndex} must be non-negative. */
    public Envelope {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(dst, "dst");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(value, "value");
        if (sendIndex < 0) {
            throw new IllegalArgumentException("sendIndex must be >= 0, was " + sendIndex);
        }
    }
}
