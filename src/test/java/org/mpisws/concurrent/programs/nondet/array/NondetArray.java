package org.mpisws.concurrent.programs.nondet.array;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.List;

public class NondetArray {

    public static void main(String[] args) {
        try {
            int SIZE = 4;
            Array array = new Array(SIZE);
            List<SetterThread> threads = new ArrayList<>(SIZE);

            for (int i = 0; i < SIZE; i++) {
                threads.add(new SetterThread(array));
            }

            SymbolicInteger n = new SymbolicInteger(false);
            ArithmeticFormula formula = new ArithmeticFormula();
            SymbolicOperation op1 = formula.geq(n, SIZE / 2);
            SymbolicOperation op2 = formula.leq(n, SIZE);
            PropositionalFormula prop = new PropositionalFormula();
            SymbolicOperation op3 = prop.and(op1, op2);
            SymbolicOperation op4 = formula.gt(n, 0);
            SymbolicOperation op5 = prop.and(op3, op4);
            Utils.assume(op5); // Assume( n >= SIZE / 2 && n <= SIZE )

            int i = 0;
            SymbolicOperation op6 = formula.gt(n, i);
            SymbolicFormula sf = new SymbolicFormula();
            for (i = 0; sf.evaluate(op6); ) {
                threads.get(i).start();
                i++;
                op6 = formula.gt(n, i);
            }

            i = 0;
            op6 = formula.gt(n, i);
            for (i = 0; sf.evaluate(op6); ) {
                threads.get(i).join();
                i++;
                op6 = formula.gt(n, i);
            }

            int sum = 0;
            i = 0;
            op6 = formula.gt(n, i);
            for (i = 0; sf.evaluate(op6); ) {
                sum += array.a[i];
                i++;
                op6 = formula.gt(n, i);
            }

            // assert (sum == SIZE - 1) : " ***The assert did not pass, the sum is " + sum + " instead of " + (SIZE - 1);
            //assert (sum <= SIZE) : " ***The assert did not pass, the sum is " + sum + " instead of " + SIZE;

        } catch (JMCInterruptException e) {

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }
    }
}
