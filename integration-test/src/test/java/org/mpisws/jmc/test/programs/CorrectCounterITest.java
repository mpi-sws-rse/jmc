package org.mpisws.jmc.test.programs;

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
}
