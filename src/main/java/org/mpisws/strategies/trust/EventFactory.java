package org.mpisws.strategies.trust;

import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.ArrayList;
import java.util.List;

public class EventFactory {

    // A special Location to represent thread events. This is used to track total order between thread start events
    // Essentially, thread starts are writes on the thread location
    public static final Location ThreadLocation = new Location("thread");

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
                Event event = new Event(runtimeEvent.getTaskId() - 1, ThreadLocation, Event.Type.NOOP);
                event.setAttribute("thread_start", true);
                return List.of(event);
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
            case FINISH_EVENT, HALT_EVENT -> {
                // Update EventUtils::isThreadFinish if anything changes here
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                ThreadLocation,
                                Event.Type.NOOP);
                event.setAttribute("thread_finish", true);
                return List.of(event);
            }
            case JOIN_COMPLETE_EVENT -> {
                // Update EventUtils::isThreadJoin if anything changes here
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                ThreadLocation,
                                Event.Type.NOOP);
                Long joinedTask = runtimeEvent.getParam("joinedTask");
                event.setAttribute("thread_join", true);
                event.setAttribute("joined_task", joinedTask -1);
                return List.of(event);
            }
        }

        return new ArrayList<>();
    }
}
