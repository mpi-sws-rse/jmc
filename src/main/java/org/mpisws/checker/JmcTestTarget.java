package org.mpisws.checker;

/** Represents a target for JMC. */
public interface JmcTestTarget {

    /** Returns the name of the target. */
    String name();

    /** Invokes the target. */
    void invoke();
}
