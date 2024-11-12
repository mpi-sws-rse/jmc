package org.mpisws.runtime;

public class ThreadNotExists extends Exception {
    public ThreadNotExists(Long threadId) {
        super("Thread does not exist: " + threadId);
    }
}
