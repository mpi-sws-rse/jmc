package org.example.concurrent.programs.mutex.common;

import org.example.concurrent.programs.mutex.Counter;

public class LocalThread extends Thread{

    private int threadId;
    private final Counter counter;
    private int incrementCount;

    public LocalThread(final Counter counter, final int incrementCount, int threadId) {
        this.counter = counter;
        this.incrementCount = incrementCount;
        this.threadId = threadId;
    }

    @Override
    public void run() {
        int incCount = 0;
        while (0 < incrementCount--) {
            counter.getAndIncrement();
            if (++incCount % 50 == 0) {
                System.out.println("Thread:" + threadId + ", iteration: " + incCount);
            }
        }
    }

    public int getThreadId() {
        return threadId;
    }
}
