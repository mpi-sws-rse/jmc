package org.mpisws.concurrent.programs.det.queue.svQueue;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Producer2 extends Thread {
    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int[] items;
    private final int SIZE;

    public Producer2(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared, int[] items) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = SIZE;
        this.shared = shared;
        this.items = items;
    }

    @Override
    public void run() {
        int c = 0;
        while (c < SIZE) {
            int i = 0;
            try {
                c++;
                lock.lock();
                if (shared.enqueue) {
                    for (i = 0; i < SIZE; i++) {
                        int x = items[i];
                        queue.enq(x);
                        shared.storedElements[0] = x;
                    }
                    shared.enqueue = false;
                    shared.dequeue = true;
                }
            } catch (JMCInterruptException e) {

            } finally {
                lock.unlock();
            }
        }
    }
}