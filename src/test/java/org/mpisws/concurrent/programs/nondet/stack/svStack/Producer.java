package org.mpisws.concurrent.programs.nondet.stack.svStack;

import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class Producer extends Thread {

    private final SVStack stack;
    private final int SIZE;
    private final ReentrantLock lock;

    public Producer(SVStack stack, int SIZE, ReentrantLock lock) {
        this.stack = stack;
        this.SIZE = SIZE;
        this.lock = lock;
    }

    @Override
    public void run() {
        try {
            int i;
            for (i = 0; i < SIZE; i++) {
                lock.lock();
                SymbolicInteger x = new SymbolicInteger("i" + i, false);
                ArithmeticFormula formula = new ArithmeticFormula();
                SymbolicOperation op = formula.lt(x, SIZE);
                Utils.assume(op); // assume (item < SIZE)
                stack.push(x);
                lock.unlock();
            }
        } catch (JMCInterruptException e) {
        }
    }
}
