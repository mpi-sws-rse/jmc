package org.mpisws.concurrent.programs.violation;

import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;

public class ThreadB extends Thread {

    private final Counter counter;

    private final SymbolicInteger x;

    public ThreadB(Counter counter, SymbolicInteger x) {
        this.counter = counter;
        this.x = x;
    }

    @Override
    public void run() {
        ArithmeticFormula formula1 = new ArithmeticFormula();
        SymbolicOperation op1 = formula1.gt(x, 0);
        SymbolicFormula symbolicFormula = new SymbolicFormula();
        if (symbolicFormula.evaluate(op1)) {

        } else {
            counter.increment();
        }
    }
}
