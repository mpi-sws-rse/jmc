package org.mpisws.jmc.test.sync;

public class SynchronizedBlockCounter implements SynchronizedCounter {
    private int count;
    private final Object lock = new Object();

    public SynchronizedBlockCounter() {
        count = 0;
    }

    public void increment() {
        synchronized (lock) {
            count++;
        }
    }

    public int getCount() {
        synchronized (lock) {
            return count;
        }
    }
}
