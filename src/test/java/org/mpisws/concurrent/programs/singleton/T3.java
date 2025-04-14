package org.mpisws.concurrent.programs.singleton;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class T3 extends Thread {

    public Shared shared;
    public ReentrantLock lock;

    public T3(Shared shared, ReentrantLock lock) {
        this.shared = shared;
        this.lock = lock;
    }

    public void run() {
        try {
            lock.lock();
            shared.c = 'Y';
            lock.unlock();
        } catch (JMCInterruptException e) {

        }
    }
}
