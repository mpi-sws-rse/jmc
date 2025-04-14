package org.mpisws.concurrent.programs.lazy01;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class T1 extends Thread {

    public ReentrantLock lock;
    public Shared shared;

    public T1(ReentrantLock lock, Shared shared) {
        this.lock = lock;
        this.shared = shared;
    }

    public void run() {
        try {
            lock.lock();
            shared.data++;
            lock.unlock();
        } catch (JMCInterruptException e) {

        }
    }
}
