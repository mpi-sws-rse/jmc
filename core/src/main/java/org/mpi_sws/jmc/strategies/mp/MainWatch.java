package org.mpi_sws.jmc.strategies.mp;

import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcUnsupportedEventException;

/**
 * Main's body, watched: records whether main's own code threw, so that a failure of the program can be told from
 * a failure of the checker. JMC's control flow (a halted task, a halted execution, a halted checker, which is also
 * how a strategy hands a fault detected elsewhere to main) is not main's failure, and neither is an
 * {@link IllegalArgumentException}, {@link IllegalStateException} or {@link NullPointerException} thrown by the
 * message-passing API itself, which reports a misuse of the API.
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
        seen = isApiRefusal(t) ? Seen.API_MISUSE : Seen.PROGRAM;
    }

    /**
     * Whether {@code t} is the message-passing API refusing a call: a {@link JmcUnsupportedEventException}, or an
     * {@link IllegalArgumentException},
     * {@link IllegalStateException} or {@link NullPointerException} whose first frame outside the JDK is in
     * {@code org.mpi_sws.jmc.api.mp} (the API validates its arguments itself or through {@code Objects}).
     */
    static boolean isApiRefusal(Throwable t) {
        if (t instanceof JmcUnsupportedEventException) {
            // The strategy cannot answer a message-passing event: refused, not the program's failure.
            return true;
        }
        if (!(t instanceof IllegalArgumentException || t instanceof IllegalStateException
                || t instanceof NullPointerException)) {
            return false;
        }
        for (StackTraceElement f : t.getStackTrace()) {
            String c = f.getClassName();
            if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.")) {
                continue;
            }
            return c.startsWith("org.mpi_sws.jmc.api.mp.");
        }
        return false;
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
