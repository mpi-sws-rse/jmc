package org.mpisws.concurrent.programs.nondet.counter.coarse;

import org.mpisws.symbolic.*;

public class DecThread extends Thread {

    public CCounter counter;
    public SymbolicInteger id;
    public String name;

    public DecThread(CCounter counter, String name) {
        this.counter = counter;
        this.id = new SymbolicInteger(name, false);
        this.name = name;
    }

    public void run() {
        ArithmeticStatement as = new ArithmeticStatement();
        as.mod(id, 2); // id % 2
        SymbolicInteger cond = new SymbolicInteger("cond-" + name, false);
        cond.assign(as); // cond = id % 2
        ArithmeticFormula af = new ArithmeticFormula();
        SymbolicOperation op = af.eq(cond, 0); // cond == 0
        SymbolicFormula sf = new SymbolicFormula();
        if (sf.evaluate(op)) { // id is even
            counter.dec1();
        } else { // id is odd
            counter.dec2();
        }
    }
}
