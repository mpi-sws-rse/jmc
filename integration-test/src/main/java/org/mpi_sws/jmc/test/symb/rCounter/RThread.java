package org.mpi_sws.jmc.test.symb.rCounter;

import org.mpi_sws.jmc.api.symbolic.SymbolicFormula;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.PropositionalFormula;
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean;
import org.mpi_sws.jmc.api.symbolic.integer.ArithmeticFormula;
import org.mpi_sws.jmc.api.symbolic.integer.ArithmeticStatement;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;

public class RThread extends Thread {

    final RCounter counter;

    final SymbolicInteger x;
    final SymbolicInteger y;
    final SymbolicBoolean a;
    final SymbolicBoolean b;

    public RThread(RCounter counter, SymbolicInteger x, SymbolicInteger y,
                   SymbolicBoolean a, SymbolicBoolean b) {
        this.counter = counter;
        this.x = x;
        this.y = y;
        this.a = a;
        this.b = b;
    }

    @Override
    public void run() {
        ArithmeticFormula formula1 = new ArithmeticFormula();
        JmcBooleanFormula op1 = formula1.geq(x, 1);

        PropositionalFormula formula2 = new PropositionalFormula();
        JmcBooleanFormula op2 = formula2.and(b, op1);

        ArithmeticStatement statement1 = new ArithmeticStatement();
        statement1.add(y, x);
        x.assign(statement1);

        ArithmeticStatement statement3 = new ArithmeticStatement();
        statement3.add(x, 1);
        x.assign(statement3);

        ArithmeticStatement statement2 = new ArithmeticStatement();
        statement2.mul(y, 3);
        y.assign(statement2);

        a.assign(formula2.not(a));

        SymbolicFormula symbolicFormula = new SymbolicFormula();
        if (symbolicFormula.evaluate(op2)) {
            JmcBooleanFormula op3 = formula1.gt(x, y);
            JmcBooleanFormula op4 = formula2.implies(b, a);
            JmcBooleanFormula op5 = formula2.and(op3, op4);
            if (symbolicFormula.evaluate(op5)) {
                counter.increment();
            }
        }
    }
}
