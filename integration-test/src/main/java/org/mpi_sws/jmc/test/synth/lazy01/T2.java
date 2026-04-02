package org.mpi_sws.jmc.test.synth.lazy01;

import java.util.concurrent.locks.ReentrantLock;

public class T2 extends Thread {

    public ReentrantLock lock;
    public Shared shared;

    public T2(ReentrantLock lock, Shared shared) {
        this.lock = lock;
        this.shared = shared;
    }

    public void run() {
        lock.lock();
        shared.data += 2;
        lock.unlock();
    }
}
