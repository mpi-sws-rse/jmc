package org.mpisws.runtime;

public class SchedulingChoice {
    private Long taskId;
    private boolean isBlockTask;
    private boolean isBlockExecution;

    private SchedulingChoice(Long taskId, boolean isBlockTask, boolean isBlockExecution) {
        this.taskId = taskId;
        this.isBlockTask = isBlockTask;
        this.isBlockExecution = isBlockExecution;
    }

    public Long getTaskId() {
        return taskId;
    }

    public boolean isBlockTask() {
        return isBlockTask;
    }

    public boolean isBlockExecution() {
        return isBlockExecution;
    }

    public static SchedulingChoice blockTask(Long taskId) {
        return new SchedulingChoice(taskId, true, false);
    }

    public static SchedulingChoice blockExecution() {
        return new SchedulingChoice(null, false, true);
    }

    public static SchedulingChoice task(Long taskId) {
        return new SchedulingChoice(taskId, false, false);
    }
}
