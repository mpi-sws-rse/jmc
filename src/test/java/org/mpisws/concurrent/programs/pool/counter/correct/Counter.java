package org.mpisws.concurrent.programs.pool.counter.correct;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Counter {

    public int value = 0;
    public ReentrantLock lock = new ReentrantLock();

    public int inc() {
        try {
            lock.lock();
            value = value + 1;
            return value;
        } catch (JMCInterruptException e) {
            return -1;
        } finally {
            lock.unlock();
        }
    }
}
