package org.mpisws.concurrent.programs.nondet.loopVariant;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class IncThread extends Thread {

    public Object lock;
    public Numbers numbers;
    public int SIZE;

    public IncThread(Object lock, Numbers numbers, int SIZE) {
        this.lock = lock;
        this.numbers = numbers;
        this.SIZE = SIZE;
    }

    @Override
    public void run() {
        try {
            int t;
            SymbolicInteger k = new SymbolicInteger(false);
            ArithmeticFormula f = new ArithmeticFormula();
            SymbolicOperation op1 = f.geq(k, SIZE / 2);
            SymbolicOperation op2 = f.leq(k, SIZE);
            PropositionalFormula prop = new PropositionalFormula();
            SymbolicOperation op3 = prop.and(op1, op2);
            Utils.assume(op3);
            synchronized (lock) {
                t = numbers.x;
                SymbolicOperation op4 = f.eq(k, numbers.n);
                SymbolicFormula sf = new SymbolicFormula();
                if (sf.evaluate(op4)) {
                    numbers.x = t + 1;
                }
            }
        } catch (JMCInterruptException e) {

        }
    }
}
