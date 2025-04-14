package org.mpisws.concurrent.programs.singleton;

import org.mpisws.util.concurrent.ReentrantLock;

public class T0 extends Thread {

    public Shared shared;
    public ReentrantLock lock;

    public T0(Shared shared, ReentrantLock lock) {
        this.shared = shared;
        this.lock = lock;
    }

    public void run() {
        T1 t1 = new T1(shared);
        t1.start();
        try {
            t1.join();
        } catch (InterruptedException e) {

        }
        T2 t2 = new T2(shared, lock);
        t2.start();
        T3 t3 = new T3(shared, lock);
        t3.start();
        T2 t4 = new T2(shared, lock);
        t4.start();
        T2 t5 = new T2(shared, lock);
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
