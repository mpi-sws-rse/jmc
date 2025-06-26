package org.mpisws.jmc.test.concrete.gcd;

import java.util.concurrent.locks.ReentrantLock;

public class DecrementorB extends Thread {

    ReentrantLock lock;
    public Numbers n;

    public DecrementorB(Numbers n, ReentrantLock lock) {
        this.n = n;
        this.lock = lock;
    }

    @Override
    public void run() {
        while (n.a != n.b) {
            lock.lock();
            if (n.b > n.a) {
                n.b = n.b - n.a;
            }
            lock.unlock();
        }
    }
}
