package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicFormula;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class Consumer4 extends Thread {

    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Consumer4(SVQueue queue, ReentrantLock lock, int SIZE, SharedState shared) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = SIZE;
        this.shared = shared;
    }

    @Override
    public void run() {
        int i;
        try {
            if (shared.dequeue) {
                lock.lock();
                for (i = 0; i < SIZE; i++) {
                    if (!queue.isEmpty()) {
                        Utils.assertion(!queue.isEmpty(), "Queue is empty");
                        AbstractInteger x = queue.deq();
                        AbstractInteger y = shared.storedElements[i];
                        ArithmeticFormula formula = new ArithmeticFormula();
                        SymbolicOperation op = formula.eq(x, y);
                        Utils.assertion(op, "Error: x != y");
                        shared.dequeue = false;
                        shared.enqueue = true;
                    }
                }
                lock.unlock();
            }
        } catch (JMCInterruptException e) {

        }
    }
}
