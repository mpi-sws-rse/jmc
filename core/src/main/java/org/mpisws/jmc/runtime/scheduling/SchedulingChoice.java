package org.mpisws.jmc.runtime.scheduling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a scheduling choice in the JMC runtime.
 *
 * <p>This class encapsulates a scheduling choice, which can either be a task to be executed, a
 * blocking task, or an end of the schedule. It also allows for the inclusion of additional values
 * associated with the scheduling choice.
 *
 * @param <T> the type of value associated with the scheduling choice
 */
public class SchedulingChoice<T extends SchedulingChoiceValue> {
    private static Logger LOGGER = LogManager.getLogger(SchedulingChoice.class);
    private Long taskId;
    private boolean isBlockTask;
    private boolean isBlockExecution;
    private final T value;

    /**
     * Constructs a new SchedulingChoice object.
     *
     * @param taskId the ID of the task associated with this scheduling choice
     * @param isBlockTask indicates if this choice is a blocking task
     * @param isBlockExecution indicates if this choice blocks execution
     */
    private SchedulingChoice(Long taskId, boolean isBlockTask, boolean isBlockExecution) {
        this.taskId = taskId;
        this.isBlockTask = isBlockTask;
        this.isBlockExecution = isBlockExecution;
        this.value = null;
    }

    /**
     * Constructs a new SchedulingChoice object with a value.
     *
     * @param taskId the ID of the task associated with this scheduling choice
     * @param value the value associated with this scheduling choice
     */
    public SchedulingChoice(Long taskId, T value) {
        this.taskId = taskId;
        this.value = value;
        this.isBlockTask = false;
        this.isBlockExecution = false;
    }

    /**
     * Returns the ID of the task associated with this scheduling choice.
     *
     * @return the ID of the task
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * Returns the value associated with this scheduling choice.
     *
     * @return the value of type T
     */
    public T getValue() {
        return value;
    }

    /**
     * Checks if this scheduling choice is a blocking task.
     *
     * @return true if it is a blocking task, false otherwise
     */
    public boolean isBlockTask() {
        return isBlockTask;
    }

    /**
     * Checks if this scheduling choice is the end of the schedule.
     *
     * @return true if it is the end of the schedule, false otherwise
     */
    public boolean isEnd() {
        return taskId == null && !isBlockTask && !isBlockExecution;
    }

    /**
     * Checks if this scheduling choice blocks execution.
     *
     * @return true if it blocks execution, false otherwise
     */
    public boolean isBlockExecution() {
        return isBlockExecution;
    }

    /**
     * Creates a scheduling choice that blocks a specific task.
     *
     * @param taskId the ID of the task to block
     * @return a SchedulingChoice that blocks the specified task
     */
    public static SchedulingChoice<?> blockTask(Long taskId) {
        return new SchedulingChoice<>(taskId, true, false);
    }

    /**
     * Creates a scheduling choice that blocks execution.
     *
     * @return a SchedulingChoice that blocks execution
     */
    public static SchedulingChoice<?> blockExecution() {
        return new SchedulingChoice<>(null, false, true);
    }

    /**
     * Creates a scheduling choice for a specific task without any value.
     *
     * @param taskId the ID of the task
     * @return a SchedulingChoice for the specified task
     */
    public static SchedulingChoice<?> task(Long taskId) {
        return new SchedulingChoice<>(taskId, false, false);
    }

    /**
     * Creates a scheduling choice for a specific task with a value.
     *
     * @param taskId the ID of the task
     * @param value the value associated with the scheduling choice
     * @param <T> the type of value associated with the scheduling choice
     * @return a SchedulingChoice for the specified task with the given value
     */
    public static <T extends SchedulingChoiceValue> SchedulingChoice<T> task(Long taskId, T value) {
        return new SchedulingChoice<>(taskId, value);
    }

    /**
     * Creates a scheduling choice that indicates the end of the task schedule.
     *
     * @return a SchedulingChoice that signifies the end of the schedule
     */
    public static SchedulingChoice<?> end() {
        // Used to indicate the end of the task schedule. Since the events occur prior to
        // a scheduling choice, the guiding schedule needs to end with a dummy event that is popped
        // in
        // the end.
        return new SchedulingChoice<>(null, false, false);
    }

    @Override
    public String toString() {
        return "SchedulingChoice{"
                + "taskId="
                + taskId
                + ", isBlockTask="
                + isBlockTask
                + ", isBlockExecution="
                + isBlockExecution
                + '}';
    }
}
