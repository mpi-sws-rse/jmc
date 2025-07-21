package org.mpisws.jmc.runtime.scheduling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;

/**
 * A factory for creating instances of {@link SchedulingChoiceValue}.
 *
 * <p>Accepts adapters for types and invokes the adapters to create values</p>
 */
public class SchedulingChoiceValueFactory {

    /**
     * A set of registered adapters for different types of {@link SchedulingChoiceValue}.
     */
    static HashMap<String, SchedulingChoiceValueAdapter<? extends SchedulingChoiceValue>> ADAPTERS =
            new HashMap<>();

    static {
        // Register default adapters for primitive types
        registerAdapter("primitive", new PrimitiveValueAdapter());
        registerAdapter("int", new PrimitiveValueAdapter());
        registerAdapter("string", new PrimitiveValueAdapter());
        registerAdapter("boolean", new PrimitiveValueAdapter());
    }
    /**
     * Registers an adapter for a specific type of {@link SchedulingChoiceValue}.
     *
     * @param type the type of the scheduling choice value, used to identify the adapter, should be the same as that returned
     *             by {@link SchedulingChoiceValue#type()}
     * @param adapter the adapter instance that converts a JSON object to a {@link SchedulingChoiceValue}
     */
    public static void registerAdapter(
            String type, SchedulingChoiceValueAdapter<? extends SchedulingChoiceValue> adapter) {
        ADAPTERS.put(type, adapter);
    }

    public static SchedulingChoiceValue create(String type, JsonElement valueObject) throws IllegalArgumentException{
        if (!ADAPTERS.containsKey(type)) {
            throw new IllegalArgumentException("No adapter registered for type: " + type);
        }

        SchedulingChoiceValueAdapter<? extends SchedulingChoiceValue> adapter = ADAPTERS.get(type);
        return adapter.fromJson(valueObject);
    }

    public static boolean containsType(String type) {
        return ADAPTERS.containsKey(type);
    }
}
