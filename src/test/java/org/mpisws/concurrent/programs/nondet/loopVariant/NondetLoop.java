package org.mpisws.concurrent.programs.nondet.loopVariant;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.List;

public class NondetLoop {

    public static void main(String[] args) {
        try {
            int SIZE = 4;
            Numbers numbers = new Numbers(0, new SymbolicInteger(false));

            ArithmeticFormula f = new ArithmeticFormula();
            SymbolicOperation op1 = f.geq(numbers.n, SIZE / 2);
            //SymbolicOperation op2 = f.leq(numbers.n, SIZE);
            SymbolicOperation op2 = f.lt(numbers.n, SIZE);
            PropositionalFormula pf = new PropositionalFormula();
            SymbolicOperation op4 = pf.and(op1, op2);
            Utils.assume(op4);

            AssertThread assertThread1 = new AssertThread(numbers);
            assertThread1.start();

            ReentrantLock lock = new ReentrantLock();
            List<IncThread> threads = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) {
                threads.add(new IncThread(lock, numbers, SIZE));
            }

            int i = 0;
            SymbolicOperation op3 = f.gt(numbers.n, i);
            SymbolicFormula sf = new SymbolicFormula();
            for (i = 0; sf.evaluate(op3); ) {
                threads.get(i).start();
                i++;
                op3 = f.gt(numbers.n, i);
            }
        } catch (JMCInterruptException e) {

        }
    }
}
