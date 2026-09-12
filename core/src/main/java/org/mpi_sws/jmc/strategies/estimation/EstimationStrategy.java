package org.mpi_sws.jmc.strategies.estimation;

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.strategies.trust.Event;
import org.mpi_sws.jmc.strategies.trust.EventFactory;
import org.mpi_sws.jmc.strategies.trust.LocationStore;

import java.util.List;

/**
 * Mixin implemented by every state-space estimation scheduling strategy.
 *
 * <p>The estimation strategies (PeStor, fork-join PeStor, Trust/Weighted-Trust estimation, and
 * TeStor) predict the number of Mazurkiewicz-trace equivalence classes {@code C(P)} of a program by
 * unbiased Monte-Carlo sampling instead of exhaustive exploration. This interface holds the small
 * pieces they share: translating runtime events into the trust event model, and committing a
 * completed trial's point estimate.
 */
public interface EstimationStrategy {

    /**
     * Translates a runtime event into the trust {@link Event}(s) the estimators consume.
     *
     * <p>Delegates to {@link EventFactory#fromRuntimeEvent}, and additionally emits a {@code NOOP}
     * event tagged {@code join-req} on the {@link LocationStore#ThreadLocation} for a {@code
     * JOIN_REQUEST_EVENT}, so estimators can observe thread joins (task ids are shifted to the
     * 0-indexed trust scheme).
     *
     * @param event the runtime event.
     * @return the corresponding trust events.
     */
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

    /** Records the point estimate of the trial that just completed into the estimation collector. */
    void recordEstimation();
}
