package org.mpisws.concurrent.programs.concrete.gcd;

import org.mpisws.util.concurrent.JmcThread;

public class DecrementorA extends JmcThread {

    Object lock;
    public Numbers n;

    public DecrementorA(Numbers n, Object lock) {
        super();
        this.n = n;
        this.lock = lock;
    }

    @Override
    public void run1() {
        while (n.a != n.b) {
            // synchronized (lock) {
            if (n.a > n.b) {
                n.a = n.a - n.b;
            }
            // }
        }
    }
}
