package org.mpisws.jmc.test;


import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread that increments a shared counter using a reentrant lock.
 * This class is used to demonstrate thread-safe incrementing of a counter.
 */
public class CounterThread extends Thread {
    private final ReentrantLock lock;
    private final Counter counter;

    public CounterThread(Counter counter, ReentrantLock lock) {
        super();
        this.counter = counter;
        this.lock = lock;
    }

    @Override
    public void run() {
        lock.lock();
        counter.set(counter.get() + 1);
        lock.unlock();
    }
}
