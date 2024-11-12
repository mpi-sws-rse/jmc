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
            int SIZE = 5;
            Numbers numbers = new Numbers(0, new SymbolicInteger(false));
            int ceil = (int) Math.ceil(SIZE / 2.0);
            ArithmeticFormula f = new ArithmeticFormula();
            SymbolicOperation op1 = f.geq(numbers.n, ceil);
            SymbolicOperation op2 = f.lt(numbers.n, SIZE);
            //SymbolicOperation op2 = f.leq(numbers.n, SIZE);
            SymbolicOperation op3 = f.gt(numbers.n, 0);

            PropositionalFormula pf = new PropositionalFormula();
            SymbolicOperation op4 = pf.and(op1, op2);
            SymbolicOperation op5 = pf.and(op4, op3);
            Utils.assume(op5);

//            AssertThread assertThread1 = new AssertThread(numbers);
//            assertThread1.start();

            ReentrantLock lock = new ReentrantLock();
            List<IncThread> threads = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) {
                threads.add(new IncThread(lock, numbers, SIZE));
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
                threads.get(i).join();
                i++;
                op6 = f.gt(numbers.n, i);
            }
        } catch (JMCInterruptException | InterruptedException e) {

        }
    }
}
