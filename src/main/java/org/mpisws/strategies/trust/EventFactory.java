package org.mpisws.strategies.trust;

import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

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
        // Subtract 1 from the task id since the runtime is 1-indexed
        switch (runtimeEvent.getType()) {
            case START_EVENT -> {
                return List.of(new Event(runtimeEvent.getTaskId() - 1, null, Event.Type.NOOP));
            }
            case WRITE_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                new Location(runtimeEvent.getParam("instance")),
                                Event.Type.WRITE);
                return List.of(event);
            }
            case READ_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                new Location(runtimeEvent.getParam("instance")),
                                Event.Type.READ);
                return List.of(event);
            }
        }

        return new ArrayList<>();
    }
}
