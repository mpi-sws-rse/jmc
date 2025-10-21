package org.mpi_sws.jmc.runtime;

/**
 * Exception thrown to halt execution of the current task. Used in the wrapped Thread and Future
 * interfaces when instrumenting.
 */
public class HaltTaskException extends RuntimeException {

    // The ID of the task that threw the exception.
    private final Long taskId;

    private final Type type;

    /**
     * Constructs a new HaltTaskException object.
     *
     * @param taskId the ID of the task that threw the exception
     */
    public HaltTaskException(Long taskId, Type type) {
        super();
        this.taskId = taskId;
        this.type = type;
    }

    public static HaltTaskException error(Long taskId, Type type) {
        return new HaltTaskException(taskId, type);
    }

    public static HaltTaskException blocked(Long taskId) {
        return new HaltTaskException(taskId, Type.BLOCKED);
    }

    public boolean isBlocked() {
        return type == Type.BLOCKED;
    }

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
     * Exception type when the model checker stops a task.
     */
    public enum Type {
        TASK_ERROR,
        BLOCKED
    }
}
