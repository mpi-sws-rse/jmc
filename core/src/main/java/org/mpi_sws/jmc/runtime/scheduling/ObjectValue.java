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

    /** The wrapped object. */
    private final Object value;

    /** Shared Gson instance used to serialize the wrapped object. */
    private static final Gson gson = new Gson();

    /**
     * Constructs a new object value wrapping the given object.
     *
     * @param value the object to wrap
     */
    public ObjectValue(Object value) {
        this.value = value;
    }

    /**
     * Returns the wrapped object.
     *
     * @return the wrapped object
     */
    public Object asObject() {
        return value;
    }

    /**
     * Serializes the wrapped object to a JSON tree using Gson.
     *
     * @return the JSON representation of the wrapped object
     */
    @Override
    public JsonElement toJson() {
        return gson.toJsonTree(value);
    }

    /**
     * Returns the type tag for object values.
     *
     * @return the string {@code "object"}
     */
    @Override
    public String type() {
        return "object";
    }
}

