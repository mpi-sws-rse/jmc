package org.mpi_sws.jmc.test.synth.singletone;


import java.util.concurrent.locks.ReentrantLock;

public class SingletoneThread2 extends Thread {

    public SingletoneShared shared;
    public ReentrantLock lock;


    public SingletoneThread2(SingletoneShared shared, ReentrantLock lock) {
        this.shared = shared;
        this.lock = lock;
    }

    public void run() {
        lock.lock();
        shared.c = 'X';
        lock.unlock();
    }
}
