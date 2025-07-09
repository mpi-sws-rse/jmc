package org.mpisws.jmc.strategies.trust;

/**
 * Exception thrown when an event does not exist.
 *
 * <p>This exception indicates that an operation was attempted on an event that is not defined in
 * the system.
 */
public class NoSuchEventException extends Exception {
    public NoSuchEventException(Event.Key key) {
        super("Event" + key.toString() + " Does not exist!");
    }
}
