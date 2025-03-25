package org.mpisws.jmc.agent.test.programs;

import java.util.concurrent.locks.ReentrantLock;

/** The CorrectCounter class is used to test the agent. */
public class CorrectCounter extends Thread {
    ReentrantLock lock;
    Counter counter;

    /**
     * The CorrectCounter constructor is used to initialize the lock and counter.
     *
     * @param lock the lock
     * @param counter the counter
     */
    public CorrectCounter(ReentrantLock lock, Counter counter) {
        this.lock = lock;
        this.counter = counter;
    }

    @Override
    public void run() {
        lock.lock();
        counter.counter++;
        lock.unlock();
    }

    /**
     * The main method is used to test the agent.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();
        Counter counter = new Counter();
        CorrectCounter[] threads = new CorrectCounter[3];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new CorrectCounter(lock, counter);
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
        assert (counter.counter == 3);
        System.out.println("All good!");
    }

    /** The Counter class is used to store the counter. */
    public static class Counter {
        int counter = 0;
    }
}
