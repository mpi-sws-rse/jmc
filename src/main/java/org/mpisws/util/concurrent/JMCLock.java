package org.mpisws.util.concurrent;

public class JMCLock {

    Object lock;

    int permits;

    public JMCLock(Object lock, int permits) {
        this.permits = permits;
        lock = new Object();
    }

    public void acquire() {
        permits = 1;
    }

    public void release() {
        permits = 0;
    }
}