package org.mpi_sws.jmc.checker;

/** Represents a target for JMC. */
public interface JmcTestTarget {

    /** Returns the name of the target. */
    String name();

    /** Invokes the target. */
    void invoke();
}
