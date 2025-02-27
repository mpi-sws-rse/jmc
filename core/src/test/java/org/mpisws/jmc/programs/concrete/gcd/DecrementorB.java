package org.mpisws.jmc.programs.concrete.gcd;

import org.mpisws.jmc.util.concurrent.JmcThread;

public class DecrementorB extends JmcThread {

    Object lock;
    public Numbers n;

    public DecrementorB(Numbers n, Object lock) {
        super();
        this.n = n;
        this.lock = lock;
    }

    @Override
    public void run1() {
        while (n.a != n.b) {
            // synchronized (lock) {
            if (n.b > n.a) {
                n.b = n.b - n.a;
            }
            // }
        }
    }
}
