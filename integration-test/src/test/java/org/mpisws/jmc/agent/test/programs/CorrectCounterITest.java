package org.mpisws.jmc.agent.test.programs;

import java.util.concurrent.locks.ReentrantLock;

/** The CorrectCounter class is used to test the agent. */
public class CorrectCounterITest extends Thread {
    ReentrantLock lock;
    CounterITest counter;

    /**
     * The CorrectCounter constructor is used to initialize the lock and counter.
     *
     * @param lock the lock
     * @param counter the counter
     */
    public CorrectCounterITest(ReentrantLock lock, CounterITest counter) {
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
        CounterITest counter = new CounterITest();
        CorrectCounterITest[] threads = new CorrectCounterITest[3];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new CorrectCounterITest(lock, counter);
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
}
