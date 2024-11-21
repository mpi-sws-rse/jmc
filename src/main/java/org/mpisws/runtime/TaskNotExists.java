package org.mpisws.runtime;

public class TaskNotExists extends Exception {
    public TaskNotExists(Long threadId) {
        super("Task does not exist: " + threadId);
    }
}
