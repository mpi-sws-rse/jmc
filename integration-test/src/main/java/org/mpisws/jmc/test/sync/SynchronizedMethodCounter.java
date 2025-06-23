package org.mpisws.jmc.test.sync;

public class SynchronizedMethodCounter implements SynchronizedCounter {
    int count;

    public SynchronizedMethodCounter() {
        count = 0;
    }

    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}
