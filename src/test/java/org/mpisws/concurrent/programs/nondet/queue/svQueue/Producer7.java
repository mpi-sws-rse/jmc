package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class Producer7 extends Thread {

    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Producer7(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared) {
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
            SymbolicInteger x = new SymbolicInteger("init" + i, false);
            queue.enq(x);
            shared.storedElements[0] = x;
            Utils.assertion(!queue.isEmpty(), "Error: queue is empty");
            lock.unlock();
            for (i = 0; i < SIZE; i++) {
                lock.lock();
                if (shared.enqueue) {
                    SymbolicInteger y = new SymbolicInteger("i" + i, false);
                    queue.enq(y);
                    shared.storedElements[i + 1] = y;
                    shared.enqueue = false;
                    shared.dequeue = true;
                }
                lock.unlock();
            }
        } catch (JMCInterruptException e) {

        }
    }
}
