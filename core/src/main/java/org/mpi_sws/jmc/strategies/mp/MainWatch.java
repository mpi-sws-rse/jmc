package org.mpi_sws.jmc.strategies.mp;

import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;

/**
 * Main's body, watched: records whether main's own code threw, so that a failure of the program can be told from
 * a failure of the checker. JMC's control flow (a halted task, a halted execution, a halted checker, which is also
 * how a strategy hands a fault detected elsewhere to main) is not main's failure, and neither is an
 * {@link IllegalStateException} thrown by the message-passing API itself, which reports a misuse of the API.
 */
public final class MainWatch implements Runnable {

    /** What main's body threw first in this run. */
    public enum Seen {
        /** Nothing that is main's own: main returned, or only JMC control flow passed through. */
        NONE,
        /** Main's own code threw: a failed assertion or an exception. */
        PROGRAM,
        /** The message-passing API refused a call of main's: a misuse of the API. */
        API_MISUSE
    }

    private final Runnable body;
    private volatile Seen seen = Seen.NONE;

    /**
     * Watches {@code body}.
     *
     * @param body main's body
     */
    public MainWatch(Runnable body) {
        this.body = body;
    }

    @Override
    public void run() {
        try {
            body.run();
        } catch (RuntimeException | Error t) {
            note(t);
            throw t;
        }
    }

    private void note(Throwable t) {
        if (seen != Seen.NONE || t instanceof HaltTaskException || t instanceof HaltExecutionException
                || t instanceof HaltCheckerException) {
            return;
        }
        seen = t instanceof IllegalStateException && thrownByTheApi(t) ? Seen.API_MISUSE : Seen.PROGRAM;
    }

    private static boolean thrownByTheApi(Throwable t) {
        StackTraceElement[] st = t.getStackTrace();
        return st.length > 0 && st[0].getClassName().startsWith("org.mpi_sws.jmc.api.mp.");
    }

    /**
     * What main's body threw first in this run.
     *
     * @return the first thing seen, {@link Seen#NONE} if nothing of main's own
     */
    public Seen seen() {
        return seen;
    }

    /**
     * What a watched program's main threw, or {@link Seen#NONE} for a program that is not watched.
     *
     * @param program the program
     * @return what its main threw
     */
    public static Seen seenBy(Runnable program) {
        return program instanceof MainWatch w ? w.seen() : Seen.NONE;
    }

    /**
     * The body watched.
     *
     * @return main's body
     */
    public Runnable body() {
        return body;
    }
}
