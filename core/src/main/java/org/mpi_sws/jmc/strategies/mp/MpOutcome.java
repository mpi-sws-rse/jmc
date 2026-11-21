package org.mpi_sws.jmc.strategies.mp;

/**
 * How a message-passing run that did not end normally ended, as the launcher's exit status reports it.
 */
public enum MpOutcome {
    /** The program failed: a failed assertion or an exception in a process or in main, a receive guard that threw. */
    VIOLATION,
    /** The run was refused: a broken rule, an unsupported model or value, a misuse of the API, an unwritable file. */
    REFUSED,
    /** The run did not finish: a timeout, an unfinished exploration, lost workers. */
    INCOMPLETE;

    /**
     * The outcome of a run whose check failed.
     *
     * @param kind the strategy's kind of the run's first fault, or {@code null}
     * @param main what main's body threw
     * @return {@link #VIOLATION} when a process or main's own code failed, {@link #REFUSED} otherwise (a broken
     *     rule, a misuse of the API, or a failure of the checker itself)
     */
    public static MpOutcome ofFailure(MpFailureKind kind, MainWatch.Seen main) {
        return kind == MpFailureKind.PROCESS || main == MainWatch.Seen.PROGRAM ? VIOLATION : REFUSED;
    }
}
