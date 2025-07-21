package org.mpisws.jmc.runtime;

/**
 * Exception thrown when a task does not exist.
 *
 * <p>This exception indicates that an operation was attempted on a task that is not found in the
 * system, typically due to an invalid thread ID.
 */
public class TaskNotExists extends Exception {
    /**
     * Constructs a new TaskNotExists exception with the specified thread ID.
     *
     * @param threadId the ID of the thread for which the task does not exist
     */
    public TaskNotExists(Long threadId) {
        super("Task does not exist: " + threadId);
    }
}
