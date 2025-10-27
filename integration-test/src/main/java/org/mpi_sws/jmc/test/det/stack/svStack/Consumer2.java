package org.mpi_sws.jmc.test.det.stack.svStack;

import java.util.concurrent.locks.ReentrantLock;

public class Consumer2 extends Thread {

    private final SVStack stack;
    private final int SIZE;
    private final ReentrantLock lock;
    private final Shared shared;

    public Consumer2(SVStack stack, int SIZE, ReentrantLock lock, Shared shared) {
        this.stack = stack;
        this.SIZE = SIZE;
        this.lock = lock;
        this.shared = shared;
    }

    @Override
    public void run() {
        int i;
        for (i = 0; i < SIZE; i++) {
            lock.lock();
            if (shared.flag) {
                if (stack.pop() == null) {
                    lock.unlock();
                    return;
                }
            }
            lock.unlock();
        }
    }
}
