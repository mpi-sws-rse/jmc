package org.mpisws.jmc.test.concrete.gcd;

import java.util.concurrent.locks.ReentrantLock;

public class DecrementorA extends Thread {

    ReentrantLock lock;
    public Numbers n;

    public DecrementorA(Numbers n, ReentrantLock lock) {
        this.n = n;
        this.lock = lock;
    }

    @Override
    public void run() {
        while (n.a != n.b) {
            lock.lock();
            if (n.a > n.b) {
                n.a = n.a - n.b;
            }
            lock.unlock();
        }
    }
}
