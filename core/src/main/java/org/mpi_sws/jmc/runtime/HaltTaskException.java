package org.mpi_sws.jmc.runtime;

/**
 * Exception thrown to halt execution of the current task. Used in the wrapped Thread and Future
 * interfaces when instrumenting.
 */
public class HaltTaskException extends RuntimeException {

    /** The ID of the task that should be halted. */
    private final Long taskId;

    /** The reason the task is being halted. */
    private final Type type;

    /**
     * Constructs a new HaltTaskException object.
     *
     * @param taskId the ID of the task that threw the exception
     * @param type the reason the task is being halted
     */
    public HaltTaskException(Long taskId, Type type) {
        super();
        this.taskId = taskId;
        this.type = type;
    }

    /**
     * Creates a {@link HaltTaskException} for the given task with the given reason.
     *
     * @param taskId the ID of the task to halt
     * @param type the reason the task is being halted
     * @return the new exception
     */
    public static HaltTaskException error(Long taskId, Type type) {
        return new HaltTaskException(taskId, type);
    }

    /**
     * Creates a {@link HaltTaskException} indicating the task is blocked.
     *
     * @param taskId the ID of the blocked task
     * @return the new exception of type {@link Type#BLOCKED}
     */
    public static HaltTaskException blocked(Long taskId) {
        return new HaltTaskException(taskId, Type.BLOCKED);
    }

    /**
     * Returns whether this exception indicates the task is blocked.
     *
     * @return {@code true} if the type is {@link Type#BLOCKED}
     */
    public boolean isBlocked() {
        return type == Type.BLOCKED;
    }

    /**
     * Returns whether this exception indicates a task error.
     *
     * @return {@code true} if the type is {@link Type#TASK_ERROR}
     */
    public boolean isTaskError() {
        return type == Type.TASK_ERROR;
    }

    /**
     * Returns the ID of the task which should be halted.
     *
     * @return the ID of the task which should be halted.
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * The reason the model checker stops a task.
     */
    public enum Type {
        /** The task raised an error and must be halted. */
        TASK_ERROR,
        /** The task is blocked (cannot make progress) and must be halted. */
        BLOCKED
    }
}
