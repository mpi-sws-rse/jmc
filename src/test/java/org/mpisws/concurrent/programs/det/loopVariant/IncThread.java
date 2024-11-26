package org.mpisws.concurrent.programs.det.loopVariant;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.JmcThread;
import org.mpisws.util.concurrent.ReentrantLock;

public class IncThread extends JmcThread {

    Numbers numbers;
    public ReentrantLock lock;
    int k;

    public IncThread(ReentrantLock lock, Numbers numbers, int k) {
        super();
        this.numbers = numbers;
        this.lock = lock;
        this.k = k;
    }

    @Override
    public void run1() {
        try {
            int t;
            lock.lock();
            t = numbers.x;
            if (k == numbers.n) {
                numbers.x = t + 1;
            }
            lock.unlock();
        } catch (JMCInterruptException e) {

        }
    }
}
