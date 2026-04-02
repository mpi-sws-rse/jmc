package org.mpi_sws.jmc.test.det.stack.svStack;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;

import java.util.concurrent.locks.ReentrantLock;

public class Producer2 extends Thread {

    private final SVStack stack;
    private final int SIZE;
    private final ReentrantLock lock;
    private final Shared shared;
    private final Integer[] items;

    public Producer2(SVStack stack, int SIZE, ReentrantLock lock, Shared shared, Integer[] items) {
        this.stack = stack;
        this.SIZE = SIZE;
        this.lock = lock;
        this.shared = shared;
        this.items = items;
    }

    @Override
    public void run() {
        int i;
        for (i = 0; i < SIZE; i++) {
            try {
                lock.lock();
                Integer x = items[i];
                JmcAssume.assume(x < SIZE); // assume (item < SIZE)
                stack.push(x);
                shared.flag = true;

            } finally {
                lock.unlock();
            }
        }
    }
}