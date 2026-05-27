package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * Represents an object value that is returned by a strategy.
 *
 * <p>A concrete implementation of the {@link SchedulingChoiceValue} class that wraps
 * any Object and serializes it to JSON when needed.</p>
 */
public class ObjectValue extends SchedulingChoiceValue {

    private final Object value;
    private static final Gson gson = new Gson();

    public ObjectValue(Object value) {
        this.value = value;
    }

    public Object asObject() {
        return value;
    }

    @Override
    public JsonElement toJson() {
        return gson.toJsonTree(value);
    }

    @Override
    public String type() {
        return "object";
    }
}

