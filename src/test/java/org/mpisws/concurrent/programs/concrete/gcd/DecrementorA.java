package org.mpisws.concurrent.programs.concrete.gcd;

public class DecrementorA extends Thread {

    Object lock;
    public Numbers n;

    public DecrementorA(Numbers n, Object lock) {
        this.n = n;
        this.lock = lock;
    }

    @Override
    public void run() {
        while (n.a != n.b) {
            //synchronized (lock) {
            if (n.a > n.b) {
                n.a = n.a - n.b;
            }
            //}
        }
    }
}
