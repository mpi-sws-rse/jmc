package org.mpi_sws.jmc.strategies.estimation;

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.strategies.trust.Event;
import org.mpi_sws.jmc.strategies.trust.EventFactory;
import org.mpi_sws.jmc.strategies.trust.LocationStore;

import java.util.List;

public interface EstimationStrategy {

    default List<Event> compileRuntimeEvent(JmcRuntimeEvent event) {
        List<Event> events = EventFactory.fromRuntimeEvent(event);
        if (event.getType() == JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT) {
            Event e =
                    new Event(
                            event.getTaskId() - 1,
                            LocationStore.ThreadLocation,
                            Event.Type.NOOP);
            e.setAttribute("join-req", true);
            events.add(e);
        }
        return events;
    }
}
