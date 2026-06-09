package org.mpi_sws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;

/**
 * A value that the strategy passes back to the runtime when resuming a task (delivered through the
 * yield return value).
 *
 * <p>The abstraction also allows such values to be serialized so they can be recorded as part of a
 * buggy trace and replayed later.</p>
 *
 * <p>For each {@link SchedulingChoiceValue} subclass there should be a corresponding {@link
 * SchedulingChoiceValueAdapter} registered with the {@link SchedulingChoiceValueFactory} to
 * reconstruct it from JSON.</p>
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
