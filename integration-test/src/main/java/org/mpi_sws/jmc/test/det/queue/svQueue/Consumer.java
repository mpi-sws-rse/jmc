package org.mpi_sws.jmc.test.det.queue.svQueue;

import java.util.concurrent.locks.ReentrantLock;

public class Consumer extends Thread {
    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Consumer(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = SIZE;
        this.shared = shared;
    }

    @Override
    public void run() {
        int i = 0;
        for (i = 0; i < SIZE; i++) {
            lock.lock();
            if (shared.dequeue) {
                int x = queue.deq();
                if (x == -1) {
                    lock.unlock();
                    return;
                }
                int y = shared.storedElements[i];
                if (y == -1) {
                    lock.unlock();
                    return;
                }

                if (x != y) {
                    lock.unlock();
                    return;
                }
                shared.dequeue = false;
                shared.enqueue = true;
            }
            lock.unlock();
        }
    }
}
