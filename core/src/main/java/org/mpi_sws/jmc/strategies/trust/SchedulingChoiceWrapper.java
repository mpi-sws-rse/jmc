package org.mpi_sws.jmc.strategies.trust;

import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

/**
 * Pairs a runtime {@link SchedulingChoice} with the memory location it acts on.
 *
 * <p>{@code ExecutionGraph.getTaskSchedule} produces a list of these when it turns a consistent
 * execution graph into a guiding schedule. Carrying the location alongside each choice lets the
 * algorithm re-map locations to the current iteration's hash codes while it replays the schedule.
 * The location may be {@code null} for choices that do not correspond to a memory access (e.g.
 * thread scheduling or the end marker).
 *
 * @param choice the runtime scheduling choice.
 * @param location the associated location hash code, or {@code null} if none.
 */
public record SchedulingChoiceWrapper(SchedulingChoice<?> choice, Integer location) {
    /**
     * Creates a new scheduling choice with the given choice and empty location.
     *
     * @param choice The choice.
     */
    public SchedulingChoiceWrapper(SchedulingChoice<?> choice) {
        this(choice, null);
    }

    /**
     * Returns whether the scheduling choice has a location.
     *
     * @return Whether the scheduling choice has a location.
     */
    public boolean hasLocation() {
        return location != null;
    }
}
