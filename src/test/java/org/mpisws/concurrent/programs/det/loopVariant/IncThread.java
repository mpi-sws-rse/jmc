package org.mpisws.concurrent.programs.det.loopVariant;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class IncThread extends Thread {

    Numbers numbers;
    public ReentrantLock lock;

    public IncThread(ReentrantLock lock, Numbers numbers) {
        this.numbers = numbers;
        this.lock = lock;
    }

    @Override
    public void run() {
        try {
            int t;
            int k = numbers.n;
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
