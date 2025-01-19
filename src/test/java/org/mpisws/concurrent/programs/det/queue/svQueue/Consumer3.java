package org.mpisws.concurrent.programs.det.queue.svQueue;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Consumer3 extends Thread {
    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Consumer3(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = SIZE;
        this.shared = shared;
    }

    @Override
    public void run() {
        int i;
        try {
            lock.lock();
            if (shared.dequeue) {
                for (i = 0; i < SIZE; i++) {
                    if (!queue.isEmpty()) {
                        int x = queue.deq();
                        if (x == -1) {
                            //System.out.println("Error: x == null");
                            return;
                        }
                        int y = shared.storedElements[i];
                        if (x != y) {
                            System.out.println("Error: x != y");
                            return;
                        }
                        shared.dequeue = false;
                        shared.enqueue = true;
                    }
                }
            }
        } catch (JMCInterruptException e) {

        } finally {
            lock.unlock();
        }
    }
}