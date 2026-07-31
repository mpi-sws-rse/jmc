package org.mpi_sws.jmc.strategies.trust;

import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates runtime events into the Trust algorithm's {@link Event}s.
 *
 * <p>This is the boundary between JMC's runtime and the Trust strategy. It maps each {@link
 * JmcRuntimeEvent} to zero or more trust events, applying the conventions the algorithm relies on:
 *
 * <ul>
 *   <li>task ids are shifted from the runtime's 1-indexed scheme to Trust's 0-indexed scheme
 *       (subtracting 1);
 *   <li>thread start/finish/join become {@code NOOP} events on {@link LocationStore#ThreadLocation}
 *       (tagged with attributes read back by {@link EventUtils});
 *   <li>a lock acquire is expanded into a {@code READ_EX}/{@code WRITE_EX} read-modify-write pair,
 *       and a lock release into a {@code WRITE};
 *   <li>unsupported runtime events yield an empty list.
 * </ul>
 */
public class EventFactory {
    /**
     * Creates a new event mapping the runtime event to the trust event.
     *
     * <p>Returns empty list if event not supported.
     *
     * @param runtimeEvent The runtime event.
     * @return A list of trust events (empty if not supported).
     */
    public static List<Event> fromRuntimeEvent(JmcRuntimeEvent runtimeEvent) {
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
                                Location.fromRuntimeEvent(runtimeEvent).hashCode(),
                                Event.Type.WRITE);
                return List.of(event);
            }
            case READ_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                Location.fromRuntimeEvent(runtimeEvent).hashCode(),
                                Event.Type.READ);
                return List.of(event);
            }
            case FINISH_EVENT /*, HALT_EVENT*/ -> {
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
            //            case JOIN_REQUEST_EVENT -> {
            //                // Update EventUtils::isThreadJoin if anything changes here
            //                Event event =
            //                        new Event(
            //                                runtimeEvent.getTaskId() - 1,
            //                                LocationStore.ThreadLocation,
            //                                Event.Type.NOOP);
            //                Long joinedTask = runtimeEvent.getParam("joinedTask");
            //                event.setAttribute("thread_join", true);
            //                event.setAttribute("joined_task", joinedTask - 1);
            //                return List.of(event);
            //            }
            case LOCK_ACQUIRE_EVENT -> {
                Event event1 =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                Location.fromRuntimeEvent(runtimeEvent).hashCode(),
                                Event.Type.READ_EX);
                event1.setAttribute("lock_acquire", true);
                Event event2 =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                Location.fromRuntimeEvent(runtimeEvent).hashCode(),
                                Event.Type.WRITE_EX);
                event2.setAttribute("lock_acquire", true);
                return List.of(event1, event2);
            }
            case LOCK_ACQUIRED_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                Location.fromRuntimeEvent(runtimeEvent).hashCode(),
                                Event.Type.NOOP);
                event.setAttribute("lock_acquired", true);
                return List.of(event);
            }
            case LOCK_RELEASE_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                Location.fromRuntimeEvent(runtimeEvent).hashCode(),
                                Event.Type.WRITE);
                event.setAttribute("lock_release", true);
                return List.of(event);
            }
            case ASSUME_EVENT -> {
                Event event = new Event(runtimeEvent.getTaskId() - 1, null, Event.Type.ASSUME);
                boolean result = runtimeEvent.getParam("result");
                event.setAttribute("result", result);
                return List.of(event);
            }
            case SYMBOLIC_EVENT -> {
                Event event =
                        new Event(
                                runtimeEvent.getTaskId() - 1,
                                null,
                                Event.Type.SYMBOLIC);
                JmcBooleanFormula formula =
                        runtimeEvent.getParam("booleanFormula");
                event.setAttribute("booleanFormula", formula);
                return List.of(event);
            }
        }

        return new ArrayList<>();
    }
}
