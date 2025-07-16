package org.mpisws.jmc.test.det.counter;

import java.util.concurrent.locks.ReentrantLock;

public class FCounter implements Counter {

    public int c1 = 0;
    public int c2 = 0;
    ReentrantLock lock1 = new ReentrantLock();
    ReentrantLock lock2 = new ReentrantLock();

    public FCounter() {
    }

    public void inc1() {
        lock1.lock();
        c1 = c1 + 1;
        lock1.unlock();
    }

    public void inc2() {
        lock2.lock();
        c2 = c2 + 1;
        lock2.unlock();
    }

    public void dec1() {
        lock1.lock();
        c1 = c1 - 1;
        lock1.unlock();
    }

    public void dec2() {
        lock2.lock();
        c2 = c2 - 1;
        lock2.unlock();
    }

}
