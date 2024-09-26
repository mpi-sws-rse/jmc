package org.mpisws.concurrent.programs.nondet.loop;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.List;

public class NondetLoop {

    public static void main(String[] args) {
        try {
            int SIZE = 10;
            Numbers numbers = new Numbers(0, new SymbolicInteger(true));

            ArithmeticFormula f = new ArithmeticFormula();
            SymbolicOperation op1 = f.geq(numbers.n, SIZE / 2);
            SymbolicOperation op2 = f.leq(numbers.n, SIZE);
            PropositionalFormula pf = new PropositionalFormula();
            SymbolicOperation op4 = pf.and(op1, op2);
            Utils.assume(op4);

            AssertThread assertThread1 = new AssertThread(numbers);
            assertThread1.start();

            List<IncThread> threads = new ArrayList<>();
            for (int i = 0; i < SIZE; i++) {
                threads.add(new IncThread(numbers));
            }

            int i = 0;
            SymbolicOperation op3 = f.gt(numbers.n, i);
            SymbolicFormula sf = new SymbolicFormula();
            for (i = 0; sf.evaluate(op3); ) {
                threads.get(i).start();
                i++;
                op3 = f.gt(numbers.n, i);
            }

//            i = 0;
//            op4 = f.gt(numbers.n, i);
//            for (i = 0; sf.evaluate(op4); ) {
//                threads.get(i).join();
//                i++;
//                op4 = f.gt(numbers.n, i);
//            }
        } catch (JMCInterruptException e) {

        }
    }
}
