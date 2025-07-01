package org.mpisws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Represents a primitive (Number|String|Boolean) value used in scheduling choices.
 *
 * <p>This class extends {@link SchedulingChoiceValue} to provide a specific implementation
 * for integer, string or boolean values, allowing them to be serialized to JSON and identified by type.</p>
 */
public class PrimitiveValueAdapter extends SchedulingChoiceValueAdapter<PrimitiveValue> {

    @Override
    public PrimitiveValue fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isString()) {
                return new PrimitiveValue(primitive.getAsString());
            } else if (primitive.isNumber()) {
                return new PrimitiveValue(primitive.getAsNumber());
            } else if (primitive.isBoolean()) {
                return new PrimitiveValue(primitive.getAsBoolean());
            } else {
                throw new IllegalArgumentException("Unsupported JSON primitive type: " + primitive);
            }
        } else {
            throw new IllegalArgumentException("Expected a JSON primitive, but got: " + json);
        }
    }
}
