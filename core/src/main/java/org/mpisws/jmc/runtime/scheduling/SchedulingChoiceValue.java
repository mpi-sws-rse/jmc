package org.mpisws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;

/**
 * A value that the strategy uses to communicate with the runtime yields.
 *
 * <p>The abstraction helps record the values when a buggy trace is found.</p>
 *
 * <p>For each {@link SchedulingChoiceValue}, there should be a </p>
 */
public abstract class SchedulingChoiceValue {

    /**
     * Converts this value to a JSON object.
     *
     * @return the JSON representation of this value
     */
    abstract public JsonElement toJson();

    /**
     * Returns the type of this value.
     *
     * <p>Default types (int|string) have inbuilt adapters.</p>
     *
     * <p>This is used to identify the type of value in the JSON representation.</p>
     *
     * @return the type of this value
     */
    abstract public String type();
}
