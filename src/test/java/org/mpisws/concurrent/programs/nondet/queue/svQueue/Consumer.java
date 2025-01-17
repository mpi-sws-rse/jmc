package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.*;
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
                    AbstractInteger x = queue.deq();
                    if (x == null) {
                        //System.out.println("Error: x == null");
                        return;
                    }
                    AbstractInteger y = shared.storedElements[i];
                    ArithmeticFormula formula = new ArithmeticFormula();
                    SymbolicOperation op = formula.neq(x, y);
                    SymbolicFormula condition = new SymbolicFormula();
                    if (condition.evaluate(op)) { // if (deq() != storedElements[i])
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
