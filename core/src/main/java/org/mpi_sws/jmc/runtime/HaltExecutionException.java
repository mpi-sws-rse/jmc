package org.mpi_sws.jmc.runtime;

/**
 * Exception thrown to halt execution of the current execution.
 */
public class HaltExecutionException extends RuntimeException {

    /** The reason this execution was halted. */
    private final Type type;

    /**
     * Constructs a new {@link HaltExecutionException} with the given type and message.
     */
    public HaltExecutionException(Type type, String message) {
        super(message);
        this.type = type;
    }

    /**
     * Constructs a new {@link HaltExecutionException} with the given type, message and cause.
     *
     * @param type the reason
     * @param message the message
     * @param cause the exception that led to the halt, preserved so callers can recover its type
     */
    public HaltExecutionException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    /**
     * Constructs a new {@link HaltExecutionException} of type error with the given message.
     */
    public static HaltExecutionException error(String message) {
        return new HaltExecutionException(Type.PROGRAM_ERROR, message);
    }

    /**
     * Constructs a new {@link HaltExecutionException} of type error that preserves its cause.
     *
     * <p>Used so that an exception a strategy throws from {@code updateEvent} (for example
     * {@link JmcUnsupportedEventException}) reaches the checker's caller with its type intact
     * rather than as message text only.
     *
     * @param message the message
     * @param cause the underlying exception
     * @return the exception
     */
    public static HaltExecutionException error(String message, Throwable cause) {
        return new HaltExecutionException(Type.PROGRAM_ERROR, message, cause);
    }

    /**
     * Constructs a new {@link HaltExecutionException} of type ALL_OK with the given message.
     */
    public static HaltExecutionException ok() {
        return new HaltExecutionException(Type.ALL_OK, "All OK");
    }

    /**
     * Constructs a new {@link HaltExecutionException} signalling that the current iteration must be
     * discarded and re-executed.
     *
     * @return the re-execution exception
     */
    public static HaltExecutionException reexecutionNeeded() {
        return new HaltExecutionException(Type.REEXECTION_NEEDED, "Re-execution needed");
    }

    /**
     * Returns whether this exception requests re-execution of the current iteration.
     *
     * @return {@code true} if the type is {@link Type#REEXECTION_NEEDED}
     */
    public boolean isReexecutionNeeded() {
        return type == Type.REEXECTION_NEEDED;
    }

    /**
     * Returns the reason this execution was halted.
     *
     * @return the halt {@link Type}
     */
    public Type getType() {
        return type;
    }

    /**
     * The reason the model checker stopped the current execution (iteration).
     */
    public enum Type {
        /** The program under test raised an error. */
        PROGRAM_ERROR,
        /** A consistency (memory model) violation was detected. */
        CONSISTENCY_VIOLATION,
        /** A deadlock was detected. */
        DEADLOCK,
        /** A race condition was detected. */
        RACE_CONDITION,
        /** The current iteration must be discarded and re-executed. */
        REEXECTION_NEEDED,
        /** The execution completed without any error. */
        ALL_OK,
    }
}
