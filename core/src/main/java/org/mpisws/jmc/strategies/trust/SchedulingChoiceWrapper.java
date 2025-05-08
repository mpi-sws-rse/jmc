package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.SchedulingChoice;

/**
 * Represents a scheduling choice with an optional location.
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
