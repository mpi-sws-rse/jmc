package org.mpi_sws.jmc.test.synth.fibBench;

import java.util.concurrent.locks.ReentrantLock;

public class FibThread2 extends Thread {

    public FibShared shared;
    public int size;
    ReentrantLock lock;

    public FibThread2(FibShared shared, int size, ReentrantLock lock) {
        this.shared = shared;
        this.size = size;
        this.lock = lock;
    }

    public void run() {
        for (int k = 0; k < size; k++) {
            lock.lock();
            shared.i = shared.i + shared.j;
            lock.unlock();
        }
    }
}
