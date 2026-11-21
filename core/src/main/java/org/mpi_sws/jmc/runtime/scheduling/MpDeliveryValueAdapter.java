package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * Reconstructs an {@link MpDeliveryValue} from its JSON form. The message itself is not in the
 * JSON (replay regenerates it by re-executing the sender), so the reconstructed answer carries
 * only the identity of the send, and a replaying strategy resolves the message from its own
 * buffers.
 */
public class MpDeliveryValueAdapter extends SchedulingChoiceValueAdapter<MpDeliveryValue> {

    /**
     * Reconstructs the answer.
     *
     * @param json {@code {"bottom": true}} or {@code {"src": n, "sendIndex": k}}
     * @return the answer; a delivery is returned with a placeholder value that a replaying
     *     strategy must replace before handing it to the task
     * @throws IllegalArgumentException if the JSON has neither shape
     */
    @Override
    public MpDeliveryValue fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object, but got: " + json);
        }
        JsonObject o = json.getAsJsonObject();
        if (o.has("bottom")) {
            return MpDeliveryValue.bottom();
        }
        if (o.has("src") && o.has("sendIndex")) {
            return MpDeliveryValue.of(
                    Tid.of(o.get("src").getAsLong()), o.get("sendIndex").getAsLong(), ReplayPlaceholder.INSTANCE);
        }
        throw new IllegalArgumentException("Not an mp-delivery value: " + json);
    }

    /**
     * Stands in for the message value during replay until the replaying strategy resolves the send
     * from its buffers; it must never reach a task.
     */
    public enum ReplayPlaceholder implements org.mpi_sws.jmc.mp.events.MpMessage {
        /** The single placeholder. */
        INSTANCE
    }
}
