package org.mpi_sws.jmc.test.synth.singletone;

import java.util.concurrent.locks.ReentrantLock;

public class SingletoneThread0 extends Thread {

    public SingletoneShared shared;
    public ReentrantLock lock;

    public SingletoneThread0(SingletoneShared shared, ReentrantLock lock) {
        this.shared = shared;
        this.lock = lock;
    }

    public void run() {
        SingletoneThread1 t1 = new SingletoneThread1(shared);
        t1.start();
        try {
            t1.join();
        } catch (InterruptedException e) {

        }
        SingletoneThread2 t2 = new SingletoneThread2(shared, lock);
        t2.start();
        SingletoneThread3 t3 = new SingletoneThread3(shared, lock);
        t3.start();
        SingletoneThread2 t4 = new SingletoneThread2(shared, lock);
        t4.start();
        SingletoneThread2 t5 = new SingletoneThread2(shared, lock);
        t5.start();

        try {
            t2.join();
            t3.join();
            t4.join();
            t5.join();
        } catch (InterruptedException e) {
        }
    }
}
