package org.mpisws.jmc.test.sync;

public class SynchronizedBlockCounter implements SynchronizedCounter {
    private int count;
    private final Object lock = new Object();

    public SynchronizedBlockCounter() {
        count = 0;
    }

    public void increment() {
        try {
            synchronized (lock) {
                try {
                    count++;
                } catch (Exception e) {
                    // Handle any exceptions that may occur during increment
                    System.err.println("Error incrementing count: " + e.getMessage());
                }
            }
        } catch (Exception ex) {

        }
    }

    public int getCount() {
        synchronized (lock) {
            return count;
        }
    }
}
