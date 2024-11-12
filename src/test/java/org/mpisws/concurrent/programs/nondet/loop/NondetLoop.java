package org.mpisws.concurrent.programs.nondet.loop;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.List;

public class NondetLoop {

    public static void main(String[] args) {
        try {
            int SIZE = 2;
            Numbers numbers = new Numbers(0, new SymbolicInteger(true));
            int ceil = (int) Math.ceil(SIZE / 2.0);
            ArithmeticFormula f = new ArithmeticFormula();
            SymbolicOperation op1 = f.geq(numbers.n, ceil);
            SymbolicOperation op2 = f.lt(numbers.n, SIZE);
            PropositionalFormula pf = new PropositionalFormula();
            SymbolicOperation op3 = pf.and(op1, op2);
            SymbolicOperation op4 = f.gt(numbers.n, 0);
            SymbolicOperation op5 = pf.and(op3, op4);
            Utils.assume(op5);

//            AssertThread assertThread1 = new AssertThread(numbers);
//            assertThread1.start();

            List<IncThread> threads = new ArrayList<>();
            for (int i = 0; i < SIZE; i++) {
                threads.add(new IncThread(numbers));
            }

            int i = 0;
            SymbolicOperation op6 = f.gt(numbers.n, i);
            SymbolicFormula sf = new SymbolicFormula();
            for (i = 0; sf.evaluate(op6); ) {
                threads.get(i).start();
                i++;
                op6 = f.gt(numbers.n, i);
            }

            i = 0;
            op6 = f.gt(numbers.n, i);
            for (i = 0; sf.evaluate(op6); ) {
                try {
                    threads.get(i).join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                i++;
                op6 = f.gt(numbers.n, i);
            }
        } catch (JMCInterruptException e) {

        }
    }
}
