package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;


public class Producer extends Thread {
    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Producer(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared) {
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
            SymbolicInteger x = new SymbolicInteger("i" + i, false);
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
                    x = new SymbolicInteger("i" + (i + 1), false);
                    queue.enq(x);
                    shared.storedElements[i + 1] = x;
                    shared.enqueue = false;
                    shared.dequeue = true;
                }
                lock.unlock();
            }
        } catch (JMCInterruptException e) {

        }
    }
}
