package org.mpi_sws.jmc.test.synth.singletone;

import java.util.concurrent.locks.ReentrantLock;

public class SingletoneThread3 extends Thread {

    public SingletoneShared shared;
    public ReentrantLock lock;

    public SingletoneThread3(SingletoneShared shared, ReentrantLock lock) {
        this.shared = shared;
        this.lock = lock;
    }

    public void run() {
        lock.lock();
        shared.c = 'Y';
        lock.unlock();
    }
}
