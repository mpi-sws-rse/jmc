package org.mpisws.concurrent.programs.symbolic.gcd;

import org.mpisws.symbolic.*;

public class Decrementor extends Thread {

    Object lock;

    private final SymbolicInteger a;

    private final SymbolicInteger b;

    public Decrementor(SymbolicInteger a, SymbolicInteger b, Object lock) {
        this.a = a;
        this.b = b;
        this.lock = lock;
    }

    @Override
    public void run() {
        ArithmeticFormula formula = new ArithmeticFormula();
        SymbolicOperation op = formula.neq(a, b);
        SymbolicFormula sf = new SymbolicFormula();
        while (sf.evaluate(op)) {
            //synchronized (lock) {
            ArithmeticFormula formula1 = new ArithmeticFormula();
            op = formula1.gt(a, b);
            if (sf.evaluate(op)) {
                ArithmeticStatement stmt = new ArithmeticStatement();
                stmt.sub(a, b);
                a.assign(stmt);
            }
            //}
            op = formula.neq(a, b);
        }
    }
}