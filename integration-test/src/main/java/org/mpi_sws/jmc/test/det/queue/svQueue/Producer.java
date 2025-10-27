package org.mpi_sws.jmc.test.det.queue.svQueue;

import java.util.concurrent.locks.ReentrantLock;

public class Producer extends Thread {
    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int[] items;
    private final int SIZE;

    public Producer(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared, int[] items) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = SIZE;
        this.shared = shared;
        this.items = items;
    }

    @Override
    public void run() {
        int i = 0;
        lock.lock();
        int x = items[i];
        queue.enq(x);
        shared.storedElements[0] = x;
        if (queue.isEmpty()) {
            lock.unlock();
            return;
        }
        lock.unlock();
        for (i = 0; i < (SIZE - 1); i++) {
            lock.lock();
            if (shared.enqueue) {
                x = items[i + 1];
                queue.enq(x);
                shared.storedElements[i + 1] = x;
                shared.enqueue = false;
                shared.dequeue = true;
            }
            lock.unlock();
        }
    }
}
