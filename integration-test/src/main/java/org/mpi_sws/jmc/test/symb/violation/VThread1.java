package org.mpi_sws.jmc.test.symb.violation;

import org.mpi_sws.jmc.api.symbolic.SymbolicFormula;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.integer.ArithmeticFormula;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;

public class VThread1 extends Thread {

    private final VCounter counter;

    private final SymbolicInteger x;

    public VThread1(VCounter counter, SymbolicInteger x) {
        this.counter = counter;
        this.x = x;
    }

    @Override
    public void run() {
        counter.getCount();
        ArithmeticFormula formula1 = new ArithmeticFormula();
        JmcBooleanFormula op1 = formula1.gt(x, 0);
        SymbolicFormula symbolicFormula = new SymbolicFormula();
        if (symbolicFormula.evaluate(op1)) {
            // NOOP
        }
    }
}
