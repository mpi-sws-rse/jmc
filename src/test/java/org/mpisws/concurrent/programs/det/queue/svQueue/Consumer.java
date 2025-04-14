package org.mpisws.concurrent.programs.det.queue.svQueue;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

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
        try {
            for (i = 0; i < SIZE; i++) {
                lock.lock();
                if (shared.dequeue) {
                    int x = queue.deq();
                    if (x == -1) {
                        return;
                    }
                    int y = shared.storedElements[i];
                    if (y == -1) {
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
        } catch (JMCInterruptException e) {

        }
    }
}
