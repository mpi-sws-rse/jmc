package org.mpi_sws.jmc.test.synth.fibBench;


import java.util.concurrent.locks.ReentrantLock;

public class FibThread1 extends Thread {

    public FibShared shared;
    public int size;
    public ReentrantLock lock;

    public FibThread1(FibShared shared, int size, ReentrantLock lock) {
        this.shared = shared;
        this.size = size;
        this.lock = lock;
    }

    public void run() {
        for (int k = 0; k < size; k++) {
            lock.lock();
            shared.j = shared.j + shared.i;
            lock.unlock();
        }
    }
}
