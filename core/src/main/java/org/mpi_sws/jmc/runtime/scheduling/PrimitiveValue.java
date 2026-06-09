package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Represents a primitive value that is returned by a strategy.
 *
 * <p>A concrete implementation of the {@link SchedulingChoiceValue} class</p>
 *
 * <p>Primitive values are one of int, string or boolean.</p>
 */
public class PrimitiveValue extends SchedulingChoiceValue{

    /** The wrapped primitive value (a {@link Number}, {@link String}, or {@link Boolean}). */
    private final Object value;

    /**
     * Constructs a new primitive value wrapping the given object.
     *
     * @param value the primitive value (expected to be a {@link Number}, {@link String}, or {@link
     *     Boolean})
     */
    public PrimitiveValue(Object value) {
        this.value = value;
    }

    /**
     * Returns the wrapped value as an {@code int}.
     *
     * @return the value as an integer
     * @throws ClassCastException if the wrapped value is not a {@link Number}
     */
    public int asInteger() {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            throw new ClassCastException("Cannot cast " + value.getClass() + " to Integer");
        }
    }

    /**
     * Returns the wrapped value as a {@link String}.
     *
     * @return the value as a string
     * @throws ClassCastException if the wrapped value is not a {@link String}
     */
    public String asString() {
        if (value instanceof String) {
            return (String) value;
        } else {
            throw new ClassCastException("Cannot cast " + value.getClass() + " to String");
        }
    }

    /**
     * Returns the wrapped value as a {@code boolean}.
     *
     * @return the value as a boolean
     * @throws ClassCastException if the wrapped value is not a {@link Boolean}
     */
    public boolean asBoolean() {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else {
            throw new ClassCastException("Cannot cast " + value.getClass() + " to Boolean");
        }
    }

    /**
     * Serializes the wrapped value to a JSON primitive.
     *
     * @return the JSON representation of the value
     * @throws IllegalArgumentException if the wrapped value is not a supported primitive type
     */
    @Override
    public JsonElement toJson() {
        if (value instanceof String) {
            return new JsonPrimitive((String) value);
        } else if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        } else if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        } else {
            throw new IllegalArgumentException("Unsupported primitive type: " + value.getClass());
        }
    }

    /**
     * Returns the type tag for primitive values.
     *
     * @return the string {@code "primitive"}
     */
    @Override
    public String type() {
        return "primitive";
    }
}
