package org.mpisws.concurrent.programs.fibBench;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class T1 extends Thread {

    public Shared shared;
    public int size;
    public ReentrantLock lock;

    public T1(Shared shared, int size, ReentrantLock lock) {
        this.shared = shared;
        this.size = size;
        this.lock = lock;
    }

    public void run() {
        for (int k = 0; k < size; k++) {
            try {
                lock.lock();
                shared.j = shared.j + shared.i;
                lock.unlock();
            } catch (JMCInterruptException e) {

            }
        }
    }
}
