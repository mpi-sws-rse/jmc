package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class Consumer7 extends Thread {

    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Consumer7(SVQueue queue, ReentrantLock lock, SharedState shared, int size) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = size;
        this.shared = shared;
    }

    @Override
    public void run() {
        int i;
        for (i = 0; i < SIZE; i++) {
            try {
                lock.lock();
                if (shared.dequeue) {
                    AbstractInteger x = queue.deq();
                    if (x == null) {
                        Utils.assertion(false, "Error: queue is empty");
                    }
                    AbstractInteger y = shared.storedElements[i];
                    if (y == null) {
                        Utils.assertion(false, "Error: y is null");
                    }
                    ArithmeticFormula f = new ArithmeticFormula();
                    SymbolicOperation op = f.eq(x, y);
                    Utils.assertion(op, "Error: x != y");
                    shared.dequeue = false;
                    shared.enqueue = true;
                }
                lock.unlock();
            } catch (JMCInterruptException e) {

            }
        }
    }
}
