package org.mpisws.concurrent.programs.nondet.counter.fine;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;

public class IncThread extends Thread {

    public FCounter counter;
    public SymbolicInteger id;
    public String name;

    public IncThread(FCounter counter, String name) {
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
            try {
                counter.inc1();
            } catch (JMCInterruptException e) {

            }
        } else { // id is odd
            try {
                counter.inc2();
            } catch (JMCInterruptException e) {

            }
        }
    }
}
