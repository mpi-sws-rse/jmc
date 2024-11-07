package org.mpisws.concurrent.programs.symbolic.counter;

import org.mpisws.symbolic.*;

public class Counter {

    private final SymbolicInteger x;

    private int count = 0;

    public Counter(SymbolicInteger x) {
        this.x = x;
    }

    public void inc() {
        ArithmeticStatement stmt = new ArithmeticStatement();
        System.out.println(
                "[Thread Message] : Thread-"
                        + Thread.currentThread().getId()
                        + " the stmt is: "
                        + stmt);
        stmt.add(x, 1);
        x.assign(stmt);
        ArithmeticFormula formula = new ArithmeticFormula();
        SymbolicOperation op = formula.gt(x, 10);
        SymbolicFormula sf = new SymbolicFormula();
        if (sf.evaluate(op)) {
            count++;
        }
    }

    public int getCount() {
        return count;
    }
}
