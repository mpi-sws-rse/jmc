package org.mpi_sws.jmc.test.symb.rCounter;

import org.mpi_sws.jmc.api.symbolic.SymbolicFormula;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.integer.ArithmeticFormula;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;

public class SRThread extends Thread {

    final SharedCounter counter;

    final SymbolicInteger x;

    public SRThread(SharedCounter counter, SymbolicInteger x) {
        this.counter = counter;
        this.x = x;
    }

    @Override
    public void run() {
        ArithmeticFormula formula1 = new ArithmeticFormula();
        JmcBooleanFormula op1 = formula1.geq(x, 1);

        SymbolicFormula symbolicFormula = new SymbolicFormula();
        if (symbolicFormula.evaluate(op1)) {
            counter.increment();
        }
    }
}
