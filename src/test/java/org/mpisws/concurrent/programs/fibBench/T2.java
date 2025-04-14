package org.mpisws.concurrent.programs.fibBench;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class T2 extends Thread {

    public Shared shared;
    public int size;
    ReentrantLock lock;

    public T2(Shared shared, int size, ReentrantLock lock) {
        this.shared = shared;
        this.size = size;
        this.lock = lock;
    }

    public void run() {
        for (int k = 0; k < size; k++) {
            try {
                lock.lock();
                shared.i = shared.i + shared.j;
                lock.unlock();
            } catch (JMCInterruptException e) {

            }
        }
    }
}
