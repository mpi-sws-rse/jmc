package org.mpisws.checker;

/*
 * Functional interface for JMC test targets.
 *
 * Targets are the methods that return nothing and take no arguments.
 */
public interface JmcTestTarget {
    /** Invokes the target. */
    void invoke();
}
