package org.mpi_sws.jmc.strategies.mp;

/**
 * What the first fault of a message-passing run was, as {@code must} and {@code mp-random} record it: a failure of
 * the program itself, or a rule of the API or of the summary format that the program broke.
 */
public enum MpFailureKind {
    /** A process failed: a failed assertion or an exception it threw, its receive guard's included. */
    PROCESS,
    /**
     * A rule was broken: a model the strategy does not support, an export shape rule, a value that cannot be
     * summarised, or another misuse of the API.
     */
    RULE
}
