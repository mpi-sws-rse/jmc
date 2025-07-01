package org.mpisws.jmc.test.sync;

public class SynchronizedMethodCounter implements SynchronizedCounter {
    int count;

    public SynchronizedMethodCounter() {
        count = 0;
    }

    public synchronized void increment() {
        try {
            count++;
        } catch (Exception e) {
            // Handle any exceptions that may occur during increment
            System.err.println("Error incrementing count: " + e.getMessage());
        }
    }

    public synchronized int getCount() {
        return count;
    }
}
