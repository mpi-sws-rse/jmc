package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class Consumer5 extends Thread {

    private final SVQueue queue;
    private final ReentrantLock lock;
    private final SharedState shared;
    private final int SIZE;

    public Consumer5(SVQueue queue, ReentrantLock lock, SharedState shared, int size) {
        this.queue = queue;
        this.lock = lock;
        this.SIZE = size;
        this.shared = shared;
    }

    @Override
    public void run() {
        int c = 0;
        while (c < SIZE) {
            c++;
            int i;
            try {
                lock.lock();
                if (shared.dequeue) {
                    for (i = 0; i < SIZE; i++) {
                        if (queue.isEmpty()) {
                            return;
                        }
                        AbstractInteger x = queue.deq();
                        AbstractInteger y = shared.storedElements[i];
                        ArithmeticFormula formula = new ArithmeticFormula();
                        SymbolicOperation op = formula.eq(x, y);
                        Utils.assertion(op, "Error: x != y");
                    }
                }
                lock.unlock();
                shared.dequeue = false;
                shared.enqueue = true;
            } catch (JMCInterruptException e) {

            }
        }
    }
}
