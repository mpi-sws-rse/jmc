package org.mpisws.jmc.runtime.scheduling;

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

    private final Object value;

    public PrimitiveValue(Object value) {
        this.value = value;
    }

    public int asInteger() {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            throw new ClassCastException("Cannot cast " + value.getClass() + " to Integer");
        }
    }

    public String asString() {
        if (value instanceof String) {
            return (String) value;
        } else {
            throw new ClassCastException("Cannot cast " + value.getClass() + " to String");
        }
    }

    public boolean asBoolean() {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else {
            throw new ClassCastException("Cannot cast " + value.getClass() + " to Boolean");
        }
    }

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

    @Override
    public String type() {
        return "primitive";
    }
}
