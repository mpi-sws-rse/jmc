package org.mpi_sws.jmc.runtime;

/**
 * Thrown when a strategy is asked to handle an event it does not model, and, on the task side,
 * when an event that required an answer came back without one.
 *
 * <p>Used by the message-passing API: a strategy that cannot answer
 * {@code MP_RECV_REQUEST_EVENT} or {@code ND_EVENT}, or that would silently ignore
 * {@code MP_SEND_EVENT}, must fail loudly with this exception rather than fall back to a
 * default. A silent fallback (for example to an unseeded {@code java.util.Random}) would make
 * executions irreproducible without any trace of why.
 */
public class JmcUnsupportedEventException extends RuntimeException {

    private final JmcRuntimeEvent.Type type;

    /**
     * Creates the exception.
     *
     * @param type the event type that was not handled
     * @param by who declined it: a strategy name, or the API method that got no answer
     */
    public JmcUnsupportedEventException(JmcRuntimeEvent.Type type, String by) {
        super(type + " is not supported by " + by);
        this.type = type;
    }

    /**
     * Returns the event type that was not handled.
     *
     * @return the type
     */
    public JmcRuntimeEvent.Type type() {
        return type;
    }
}
