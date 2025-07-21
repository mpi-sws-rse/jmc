package org.mpisws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;

/**
 * An adapter to convert a JSON object to a {@link SchedulingChoiceValue}.
 *
 * <p>Each {@link SchedulingChoiceValue} should have a corresponding adapter.</p>
 *
 * @param <T> the type of {@link SchedulingChoiceValue} this adapter converts to
 */
public abstract class SchedulingChoiceValueAdapter<T extends SchedulingChoiceValue> {

    /**
     * Converts a JSON object to a {@link SchedulingChoiceValue}.
     *
     * @param json the JSON object to convert
     * @return the converted {@link SchedulingChoiceValue}
     */
    abstract T fromJson(JsonElement json);
}
