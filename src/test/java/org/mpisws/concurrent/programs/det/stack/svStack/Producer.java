package org.mpisws.concurrent.programs.det.stack.svStack;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class Producer extends Thread {

    private final SVStack stack;
    private final int SIZE;
    private final ReentrantLock lock;
    private final Integer[] items;

    public Producer(SVStack stack, int SIZE, ReentrantLock lock, Integer[] items) {
        this.stack = stack;
        this.SIZE = SIZE;
        this.lock = lock;
        this.items = items;
    }

    @Override
    public void run() {
        try {
            int i;
            for (i = 0; i < SIZE; i++) {
                lock.lock();
                Integer x = items[i];
                Utils.assume(x < SIZE); // assume (item < SIZE)
                stack.push(x);
                lock.unlock();
            }
        } catch (JMCInterruptException e) {
        } finally {
            lock.unlock();
        }
    }
}
