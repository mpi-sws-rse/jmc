package org.mpisws.jmc.runtime.scheduling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SchedulingChoice<T extends SchedulingChoiceValue> {
    private static Logger LOGGER = LogManager.getLogger(SchedulingChoice.class);
    private Long taskId;
    private boolean isBlockTask;
    private boolean isBlockExecution;
    private final T value;

    private SchedulingChoice(Long taskId, boolean isBlockTask, boolean isBlockExecution) {
        this.taskId = taskId;
        this.isBlockTask = isBlockTask;
        this.isBlockExecution = isBlockExecution;
        this.value = null;
    }

    public SchedulingChoice(Long taskId, T value) {
        this.taskId = taskId;
        this.value = value;
        this.isBlockTask = false;
        this.isBlockExecution = false;
    }

    public Long getTaskId() {
        return taskId;
    }

    public T getValue() {
        return value;
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

    public static SchedulingChoice<?> blockTask(Long taskId) {
        return new SchedulingChoice<>(taskId, true, false);
    }

    public static SchedulingChoice<?> blockExecution() {
        return new SchedulingChoice<>(null, false, true);
    }

    public static SchedulingChoice<?> task(Long taskId) {
        return new SchedulingChoice<>(taskId, false, false);
    }

    public static <T extends SchedulingChoiceValue> SchedulingChoice<T> task(Long taskId, T value) {
        return new SchedulingChoice<>(taskId, value);
    }

    // Used to indicate the end of the task schedule. Since the events occur prior to
    // a scheduling choice, the guiding schedule needs to end with a dummy event that is popped in
    // the end.
    public static SchedulingChoice<?> end() {
        return new SchedulingChoice<>(null, false, false);
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
