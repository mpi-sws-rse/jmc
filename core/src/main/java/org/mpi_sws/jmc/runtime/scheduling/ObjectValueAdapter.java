package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;

/**
 * Adapter that reconstructs an {@link ObjectValue} from its JSON representation.
 *
 * <p>Registered with the {@link SchedulingChoiceValueFactory} for the {@code "object"} type tag.</p>
 */
public class ObjectValueAdapter extends SchedulingChoiceValueAdapter<ObjectValue> {

    /**
     * Reconstructs an {@link ObjectValue} from a JSON object.
     *
     * @param json the JSON element to convert
     * @return the reconstructed object value, wrapping the JSON object
     * @throws IllegalArgumentException if {@code json} is not a JSON object
     */
    @Override
    public ObjectValue fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object, but got: " + json);
        }
        return new ObjectValue(json.getAsJsonObject());
    }
}

