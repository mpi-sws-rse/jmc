package org.mpisws.concurrent.programs.lazy01;

import org.mpisws.util.concurrent.ReentrantLock;

public class Lazy01 {

    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();
        Shared shared = new Shared();
        T1 t1 = new T1(lock, shared);
        T2 t2 = new T2(lock, shared);
        T3 t3 = new T3(lock, shared);

        t1.start();
        t2.start();
        t3.start();

        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (InterruptedException e) {

        }
    }
}
