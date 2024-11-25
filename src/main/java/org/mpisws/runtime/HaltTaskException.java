package org.mpisws.runtime;

/**
 * Exception thrown to halt execution of the current task. Used in the wrapped Thread and Future
 * interfaces when instrumenting.
 */
public class HaltTaskException extends RuntimeException {

    // The ID of the task that threw the exception.
    private final Long taskId;

    /**
     * Constructs a new HaltTaskException object.
     *
     * @param taskId the ID of the task that threw the exception
     */
    public HaltTaskException(Long taskId) {
        super();
        this.taskId = taskId;
    }

    /**
     * Constructs a new HaltTaskException object.
     *
     * @param taskId the ID of the task that threw the exception
     * @param message the message to be displayed
     */
    public HaltTaskException(Long taskId, String message) {
        super(message);
        this.taskId = taskId;
    }

    /**
     * Returns the ID of the task which should be halted.
     *
     * @return the ID of the task which should be halted.
     */
    public Long getTaskId() {
        return taskId;
    }
}
