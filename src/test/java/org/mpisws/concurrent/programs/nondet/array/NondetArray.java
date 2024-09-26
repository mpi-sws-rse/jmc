package org.mpisws.concurrent.programs.nondet.array;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.List;

public class NondetArray {

    public static void main(String[] args) {
        try {
            int SIZE = 10;
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
            Utils.assume(op3); // Assume( n >= SIZE / 2 && n <= SIZE )

            int i = 0;
            SymbolicOperation op4 = formula.gt(n, i);
            SymbolicFormula sf = new SymbolicFormula();
            for (i = 0; sf.evaluate(op4); ) {
                threads.get(i).start();
                i++;
                op4 = formula.gt(n, i);
            }

            i = 0;
            op4 = formula.gt(n, i);
            for (i = 0; sf.evaluate(op4); ) {
                threads.get(i).join();
                i++;
                op4 = formula.gt(n, i);
            }

            int sum = 0;
            i = 0;
            op4 = formula.gt(n, i);
            for (i = 0; sf.evaluate(op4); ) {
                sum += array.a[i];
                i++;
                op4 = formula.gt(n, i);
            }

            assert (sum == SIZE - 1) : " ***The assert did not pass, the sum is " + sum + " instead of " + (SIZE - 1);

        } catch (JMCInterruptException e) {

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }
    }
}
