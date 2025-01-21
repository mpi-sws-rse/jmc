package org.mpisws.concurrent.programs.det.counter.fine;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class FCounter {

    public int c1 = 0;
    public int c2 = 0;
    ReentrantLock lock1 = new ReentrantLock();
    ReentrantLock lock2 = new ReentrantLock();

    public FCounter() {
    }

    public void inc1() throws JMCInterruptException {
        lock1.lock();
        c1 = c1 + 1;
        lock1.unlock();
    }

    public void inc2() throws JMCInterruptException {
        lock2.lock();
        c2 = c2 + 1;
        lock2.unlock();
    }

    public void dec1() throws JMCInterruptException {
        lock1.lock();
        c1 = c1 - 1;
        lock1.unlock();
    }

    public void dec2() throws JMCInterruptException {
        lock2.lock();
        c2 = c2 - 1;
        lock2.unlock();
    }

}
