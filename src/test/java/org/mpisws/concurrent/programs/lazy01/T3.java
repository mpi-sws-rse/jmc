package org.mpisws.concurrent.programs.lazy01;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class T3 extends Thread {

    public ReentrantLock lock;
    public Shared shared;

    public T3(ReentrantLock lock, Shared shared) {
        this.lock = lock;
        this.shared = shared;
    }

    public void run() {
        try {
            lock.lock();
            Utils.assertion(shared.data < 3, "Error: " + shared.data);
            lock.unlock();
        } catch (JMCInterruptException e) {

        }
    }
}
