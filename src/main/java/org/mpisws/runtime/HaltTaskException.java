package org.mpisws.runtime;

public class HaltTaskException extends RuntimeException {

    private Long taskId;

    public HaltTaskException(Long taskId) {
        super();
        this.taskId = taskId;
    }

    public HaltTaskException(Long taskId, String message) {
        super(message);
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
