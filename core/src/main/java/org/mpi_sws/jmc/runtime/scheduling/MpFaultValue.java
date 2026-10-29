package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * A strategy-detected fault, delivered to a task that is waiting for a message-passing answer so
 * that the fault surfaces on <em>that task's</em> thread.
 *
 * <p>Why this exists: a message-passing strategy can detect a fault on a thread whose exceptions
 * the runtime only logs (a spawned task's), while the task that must fail the run (main) is
 * parked on a receive and will emit no further event of its own. The strategy then resumes the
 * parked task with this value instead of a delivery; the API's unwrapping of the answer
 * ({@code Channel.request}) converts it into a {@code HaltExecutionException} carrying
 * {@link #message()}, which the checker reports as the run's error.
 *
 * <p>Never recorded in a trace and never replayed: it is not a choice, and the run it appears in
 * has failed. No adapter is registered for it.
 */
public final class MpFaultValue extends SchedulingChoiceValue {

    /** The type tag. Not registered with {@link SchedulingChoiceValueFactory}; see class doc. */
    public static final String TYPE = "mp-fault";

    private final String message;

    /**
     * Creates a fault value.
     *
     * @param message the fault, as the strategy described it
     */
    public MpFaultValue(String message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * The fault message.
     *
     * @return the message
     */
    public String message() {
        return message;
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("message", message);
        return o;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "fault(" + message + ")";
    }
}
