package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Adapter that reconstructs a {@link PrimitiveValue} from its JSON representation.
 *
 * <p>Handles JSON primitives of string, number, and boolean kind. Registered with the {@link
 * SchedulingChoiceValueFactory} for the primitive type tags.</p>
 */
public class PrimitiveValueAdapter extends SchedulingChoiceValueAdapter<PrimitiveValue> {

    /**
     * Reconstructs a {@link PrimitiveValue} from a JSON primitive (string, number, or boolean).
     *
     * @param json the JSON element to convert
     * @return the reconstructed primitive value
     * @throws IllegalArgumentException if {@code json} is not a supported JSON primitive
     */
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
