package org.mpisws.runtime;

/** Exception thrown to halt execution of the current execution. */
public class HaltExecutionException extends RuntimeException {

    private final Type type;

    /** Constructs a new {@link HaltExecutionException} with the given type and message. */
    public HaltExecutionException(Type type, String message) {
        super(message);
        this.type = type;
    }

    /** Constructs a new {@link HaltExecutionException} of type error with the given message. */
    public static HaltExecutionException error(String message) {
        return new HaltExecutionException(Type.PROGRAM_ERROR, message);
    }

    /** Constructs a new {@link HaltExecutionException} of type ALL_OK with the given message. */
    public static HaltExecutionException ok() {
        return new HaltExecutionException(Type.ALL_OK, "All OK");
    }

    public Type getType() {
        return type;
    }

    /** Error type when the model checker stops the execution. */
    public enum Type {
        PROGRAM_ERROR,
        CONSISTENCY_VIOLATION,
        DEADLOCK,
        RACE_CONDITION,
        ALL_OK,
    }
}
