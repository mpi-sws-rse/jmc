package org.mpisws.strategies.trust;

public class NoSuchEventException extends Exception {
    public NoSuchEventException(Event.Key key) {
        super("Event" + key.toString() + " Does not exist!");
    }
}
