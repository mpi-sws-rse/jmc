package org.mpisws.strategies.trust;

import org.mpisws.runtime.RuntimeEvent;

import java.util.ArrayList;
import java.util.List;

public class EventFactory {

    /**
     * Creates a new event mapping the runtime event to the trust event.
     *
     * <p>Returns empty list if event not supported.
     *
     * @param runtimeEvent The runtime event.
     * @return A list of trust events (empty if not supported).
     */
    public static List<Event> fromRuntimeEvent(RuntimeEvent runtimeEvent) {

        return new ArrayList<>();
    }
}
