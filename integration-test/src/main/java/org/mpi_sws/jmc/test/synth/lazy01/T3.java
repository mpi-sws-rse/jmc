package org.mpi_sws.jmc.test.synth.lazy01;

import java.util.concurrent.locks.ReentrantLock;

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
            assert shared.data < 3 : "Error";
        } finally {
            lock.unlock();
        }
    }
}
