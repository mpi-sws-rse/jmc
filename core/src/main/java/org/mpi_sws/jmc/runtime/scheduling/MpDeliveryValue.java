package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.Objects;

/**
 * The answer a strategy attaches to a {@code SchedulingChoice} to resolve an
 * {@code MP_RECV_REQUEST_EVENT}: the send that the receive reads, identified by the pair
 * {@code (src, sendIndex)}, together with its message; or {@code ⊥} ("bottom": a non-blocking
 * receive that reads nothing).
 *
 * <p>{@link #toJson()} serializes <em>only</em> the identity ({@code src}, {@code sendIndex}) or
 * the {@code ⊥} marker, never the value: replay re-executes the program, so the sender regenerates
 * the message and only the <em>choice</em> needs to survive to JSON. Serializing arbitrary user
 * message objects would be fragile and would record data replay does not need.
 */
public final class MpDeliveryValue extends SchedulingChoiceValue {

    /** The type tag registered with {@link SchedulingChoiceValueFactory}. */
    public static final String TYPE = "mp-delivery";

    private static final MpDeliveryValue BOTTOM = new MpDeliveryValue(null, -1, null);

    private final Tid src;
    private final long sendIndex;
    private final MpMessage value;

    private MpDeliveryValue(Tid src, long sendIndex, MpMessage value) {
        this.src = src;
        this.sendIndex = sendIndex;
        this.value = value;
    }

    /**
     * Creates the answer that delivers a send.
     *
     * @param src the sending task
     * @param sendIndex the send's index among {@code src}'s sends in this iteration
     * @param value the message
     * @return the answer
     */
    public static MpDeliveryValue of(Tid src, long sendIndex, MpMessage value) {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(value, "value");
        if (sendIndex < 0) {
            throw new IllegalArgumentException("sendIndex must be >= 0");
        }
        return new MpDeliveryValue(src, sendIndex, value);
    }

    /**
     * The {@code ⊥} answer: a non-blocking receive reads nothing.
     *
     * @return the bottom answer
     */
    public static MpDeliveryValue bottom() {
        return BOTTOM;
    }

    /**
     * Whether this is {@code ⊥}.
     *
     * @return {@code true} iff bottom
     */
    public boolean isBottom() {
        return src == null;
    }

    /**
     * The sending task.
     *
     * @return the sender
     * @throws IllegalStateException if this is {@code ⊥}
     */
    public Tid src() {
        requireDelivery();
        return src;
    }

    /**
     * The send's index among the sender's sends.
     *
     * @return the index
     * @throws IllegalStateException if this is {@code ⊥}
     */
    public long sendIndex() {
        requireDelivery();
        return sendIndex;
    }

    /**
     * The message.
     *
     * @return the value
     * @throws IllegalStateException if this is {@code ⊥}
     */
    public MpMessage value() {
        requireDelivery();
        return value;
    }

    private void requireDelivery() {
        if (isBottom()) {
            throw new IllegalStateException("⊥ delivers no message");
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        if (isBottom()) {
            o.addProperty("bottom", true);
        } else {
            o.addProperty("src", src.raw());
            o.addProperty("sendIndex", sendIndex);
        }
        return o;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return isBottom() ? "⊥" : "deliver(" + src + "#" + sendIndex + ")";
    }
}
