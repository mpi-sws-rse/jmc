package org.mpisws.concurrent.programs.nondet.stack.svStack;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Consumer extends Thread {

    private final SVStack stack;
    private final int SIZE;
    private final ReentrantLock lock;

    public Consumer(SVStack stack, int SIZE, ReentrantLock lock) {
        this.stack = stack;
        this.SIZE = SIZE;
        this.lock = lock;
    }

    @Override
    public void run() {
        try {
            int i;
            for (i = 0; i < SIZE; i++) {
                lock.lock();
                if (stack.getTop() > 0) {
                    if (stack.pop() == null) {
                        return;
                    }
                }
                lock.unlock();
            }
        } catch (JMCInterruptException e) {
        } finally {
            lock.unlock();
        }
    }
}
