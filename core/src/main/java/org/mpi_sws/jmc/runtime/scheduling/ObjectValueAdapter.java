package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;

/**
 * Adapter for converting JSON object values into {@link ObjectValue}.
 */
public class ObjectValueAdapter extends SchedulingChoiceValueAdapter<ObjectValue> {

    @Override
    public ObjectValue fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object, but got: " + json);
        }
        return new ObjectValue(json.getAsJsonObject());
    }
}

