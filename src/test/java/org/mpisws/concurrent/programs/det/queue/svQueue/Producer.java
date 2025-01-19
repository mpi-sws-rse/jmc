package org.mpisws.concurrent.programs.det.queue.svQueue;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

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
        try {
            lock.lock();
            int x = items[i];
            queue.enq(x);
            shared.storedElements[0] = x;

            if (queue.isEmpty()) {
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
        } catch (JMCInterruptException e) {

        } finally {
            lock.unlock();
        }
    }
}
