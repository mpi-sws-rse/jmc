package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.RuntimeEvent;

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
        // Note: Subtract 1 from the task id since the runtime is 1-indexed
        switch (runtimeEvent.getType()) {
            case START_EVENT -> {
                // Update EventUtils::isThreadStart if anything changes here
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                LocationStore.ThreadLocation,
                                Event.Type.NOOP);
                event.setAttribute("thread_start", true);
                Long startedBy = runtimeEvent.getParam("startedBy");
                event.setAttribute("started_by", startedBy - 1);
                return List.of(event);
            }
            case WRITE_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                runtimeEvent.getParam("instance").hashCode(),
                                Event.Type.WRITE);
                return List.of(event);
            }
            case READ_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                runtimeEvent.getParam("instance").hashCode(),
                                Event.Type.READ);
                return List.of(event);
            }
            case FINISH_EVENT, HALT_EVENT -> {
                // Update EventUtils::isThreadFinish if anything changes here
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                LocationStore.ThreadLocation,
                                Event.Type.NOOP);
                event.setAttribute("thread_finish", true);
                return List.of(event);
            }
            case JOIN_COMPLETE_EVENT -> {
                // Update EventUtils::isThreadJoin if anything changes here
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                LocationStore.ThreadLocation,
                                Event.Type.NOOP);
                Long joinedTask = runtimeEvent.getParam("joinedTask");
                event.setAttribute("thread_join", true);
                event.setAttribute("joined_task", joinedTask - 1);
                return List.of(event);
            }
        }

        return new ArrayList<>();
    }
}
