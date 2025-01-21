package org.mpisws.concurrent.programs.det.counter.coarse;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class CCounter {

    public int c1 = 0;
    public int c2 = 0;
    ReentrantLock lock = new ReentrantLock();

    public CCounter() {
    }

    public void inc1() throws JMCInterruptException {
        lock.lock();
        c1 = c1 + 1;
        lock.unlock();
    }

    public void inc2() throws JMCInterruptException {
        lock.lock();
        c2 = c2 + 1;
        lock.unlock();
    }

    public void dec1() throws JMCInterruptException {
        lock.lock();
        c1 = c1 - 1;
        lock.unlock();
    }

    public void dec2() throws JMCInterruptException {
        lock.lock();
        c2 = c2 - 1;
        lock.unlock();
    }
}
