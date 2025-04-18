package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Producer3 extends Thread {
    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Producer3(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = SIZE;
        this.shared = shared;
    }

    @Override
    public void run() {
        int i = 0;
        try {
            lock.lock();
            if (shared.enqueue) {
                for (i = 0; i < SIZE; i++) {
                    SymbolicInteger x = new SymbolicInteger("i" + i, false);
                    queue.enq(x);
                    shared.storedElements[i] = x;
                }
                shared.enqueue = false;
                shared.dequeue = true;
            }
            lock.unlock();
        } catch (JMCInterruptException e) {

        }
    }
}