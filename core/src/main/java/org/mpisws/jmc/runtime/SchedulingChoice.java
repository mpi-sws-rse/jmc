package org.mpisws.jmc.runtime;

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

    public boolean isEnd() {
        return taskId == null && !isBlockTask && !isBlockExecution;
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
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        return new SchedulingChoice(taskId, false, false);
    }

    // Used to indicate the end of the task schedule. Since the events occur prior to
    // a scheduling choice, the guiding schedule needs to end with a dummy event that is popped in
    // the end.
    public static SchedulingChoice end() {
        return new SchedulingChoice(null, false, false);
    }

    @Override
    public String toString() {
        return "SchedulingChoice{" +
                "taskId=" + taskId +
                ", isBlockTask=" + isBlockTask +
                ", isBlockExecution=" + isBlockExecution +
                '}';
    }
}
