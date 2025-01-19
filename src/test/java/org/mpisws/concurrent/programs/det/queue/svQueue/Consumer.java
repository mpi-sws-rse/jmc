package org.mpisws.concurrent.programs.det.queue.svQueue;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

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
                        //System.out.println("Error: x == null");
                        return;
                    }
                    int y = shared.storedElements[i];
                    if (y == -1) {
                        //System.out.println("Error: y == null");
                        return;
                    }
                    if (x != y) { // if (deq() != storedElements[i])
                        System.out.println("Error: x != y");
                        return;
                    }
                    shared.dequeue = false;
                    shared.enqueue = true;
                }
                lock.unlock();
            }
        } catch (JMCInterruptException e) {

        } finally {
            lock.unlock();
        }
    }
}
