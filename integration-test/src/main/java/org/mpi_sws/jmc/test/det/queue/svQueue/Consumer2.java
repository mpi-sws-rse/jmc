package org.mpi_sws.jmc.test.det.queue.svQueue;

import java.util.concurrent.locks.ReentrantLock;

public class Consumer2 extends Thread {
    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Consumer2(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = SIZE;
        this.shared = shared;
    }

    @Override
    public void run() {
        int c = 0;
        while (c < SIZE) {
            c++;
            int i;
            try {
                lock.lock();
                if (shared.dequeue) {
                    for (i = 0; i < SIZE; i++) {
                        if (!queue.isEmpty()) {
                            int x = queue.deq();
                            if (x == -1) {
                                //(Error: x == null)
                                return;
                            }
                            int y = shared.storedElements[i];
                            if (x != y) { // if (deq() != storedElements[i])
                                System.out.println("Error: x != y");
                                return;
                            }
                            shared.dequeue = false;
                            shared.enqueue = true;
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}