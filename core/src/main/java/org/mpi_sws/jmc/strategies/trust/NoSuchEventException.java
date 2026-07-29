package org.mpi_sws.jmc.strategies.trust;

/**
 * Checked exception thrown when a lookup by {@link Event.Key} finds no matching node in an {@link
 * ExecutionGraph}.
 *
 * <p>Raised by {@code ExecutionGraph.getEventNode} when the key refers to an event that is not (or
 * no longer) present — for example after a revisit deleted it — and is typically caught and
 * translated into a checker halt by the caller.
 */
public class NoSuchEventException extends Exception {
    /**
     * Creates the exception for the missing event key.
     *
     * @param key the key that could not be resolved.
     */
    public NoSuchEventException(Event.Key key) {
        super("Event" + key.toString() + " Does not exist!");
    }
}
