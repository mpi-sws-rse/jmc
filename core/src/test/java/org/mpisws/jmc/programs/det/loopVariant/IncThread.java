package org.mpisws.jmc.programs.det.loopVariant;

import org.mpisws.jmc.api.util.concurrent.JmcThread;
import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;

public class IncThread extends JmcThread {

    Numbers numbers;
    public JmcReentrantLock lock;
    int k;

    public IncThread(JmcReentrantLock lock, Numbers numbers, int k) {
        super();
        this.numbers = numbers;
        this.lock = lock;
        this.k = k;
    }

    @Override
    public void run1() {
//        try {
            int t;
            lock.lock();
            t = numbers.x;
            if (k == numbers.n) {
                numbers.x = t + 1;
            }
            lock.unlock();
//        } catch (JMCInterruptException e) {
//
//        }
    }
}
