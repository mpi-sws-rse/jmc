package org.mpisws.jmc.test;


import java.util.concurrent.locks.ReentrantLock;

public class ParametricCounter {

    private final Counter counter;
    private final ReentrantLock lock;
    private final int numThreads;

    public ParametricCounter(int numThreads) {
        this.numThreads = numThreads;
        this.counter = new Counter();
        this.lock = new ReentrantLock();
    }

    public void run() {
        int threadCount = numThreads;
        CounterThread[] threads = new CounterThread[threadCount];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new CounterThread(counter, lock);
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public int getCounterValue() {
        return counter.get();
    }
}
