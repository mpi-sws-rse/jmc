package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Producer5 extends Thread {

    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Producer5(SVQueue queue, ReentrantLock lock, SharedState shared, int size) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = size;
        this.shared = shared;
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
                        SymbolicInteger x = new SymbolicInteger("i" + i, false);
                        queue.enq(x);
                        shared.storedElements[i] = x;
                    }
                }
                lock.unlock();
                shared.enqueue = false;
                shared.dequeue = true;
            } catch (JMCInterruptException e) {

            }
        }
    }
}
