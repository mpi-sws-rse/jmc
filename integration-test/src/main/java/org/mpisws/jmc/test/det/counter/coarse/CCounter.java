package org.mpisws.jmc.test.det.counter.coarse;

import java.util.concurrent.locks.ReentrantLock;

public class CCounter {

    public int c1 = 0;
    public int c2 = 0;
    ReentrantLock lock = new ReentrantLock();

    public CCounter() {
    }

    public void inc1() {
        lock.lock();
        c1 = c1 + 1;
        lock.unlock();
    }

    public void inc2() {
        lock.lock();
        c2 = c2 + 1;
        lock.unlock();
    }

    public void dec1() {
        lock.lock();
        c1 = c1 - 1;
        lock.unlock();
    }

    public void dec2() {
        lock.lock();
        c2 = c2 - 1;
        lock.unlock();
    }
}
