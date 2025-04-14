package org.mpisws.concurrent.programs.lazy01;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class T2 extends Thread {

    public ReentrantLock lock;
    public Shared shared;

    public T2(ReentrantLock lock, Shared shared) {
        this.lock = lock;
        this.shared = shared;
    }

    public void run() {
        try {
            lock.lock();
            shared.data += 2;
            lock.unlock();
        } catch (JMCInterruptException e) {

        }
    }
}
