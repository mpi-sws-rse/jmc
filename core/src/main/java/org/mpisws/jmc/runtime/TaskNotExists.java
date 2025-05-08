package org.mpisws.jmc.runtime;

public class TaskNotExists extends Exception {
    public TaskNotExists(Long threadId) {
        super("Task does not exist: " + threadId);
    }
}
