package org.mpisws.concurrent.programs.random.counter;

import org.mpisws.symbolic.*;

public class RandomCounter extends Thread {

    Counter counter;

    SymbolicInteger x;
    SymbolicInteger y;
    SymbolicBoolean a;
    SymbolicBoolean b;

    public RandomCounter(
            Counter counter,
            SymbolicInteger x,
            SymbolicInteger y,
            SymbolicBoolean a,
            SymbolicBoolean b) {
        this.counter = counter;
        this.x = x;
        this.y = y;
        this.a = a;
        this.b = b;
    }

    @Override
    public void run() {
        ArithmeticFormula formula1 = new ArithmeticFormula();
        SymbolicOperation op1 = formula1.geq(x, 1);

        PropositionalFormula formula2 = new PropositionalFormula();
        SymbolicOperation op2 = formula2.and(b, op1);

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
            SymbolicOperation op3 = formula1.gt(x, y);
            SymbolicOperation op4 = formula2.implies(b, a);
            SymbolicOperation op5 = formula2.and(op3, op4);
            if (symbolicFormula.evaluate(op5)) {
                counter.increment();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        SymbolicInteger x1 = new SymbolicInteger(false);
        SymbolicInteger x2 = new SymbolicInteger(false);
        SymbolicInteger y1 = new SymbolicInteger(false);
        SymbolicInteger y2 = new SymbolicInteger(false);
        SymbolicBoolean a1 = new SymbolicBoolean(false);
        SymbolicBoolean a2 = new SymbolicBoolean(false);
        SymbolicBoolean b1 = new SymbolicBoolean(false);
        SymbolicBoolean b2 = new SymbolicBoolean(false);
        RandomCounter thread1 = new RandomCounter(counter, x1, y1, a1, b1);
        RandomCounter thread2 = new RandomCounter(counter, x2, y2, a2, b2);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        try {
            assert (counter.getValue() == 2)
                    : " ***The assert did not pass, the counter value is "
                            + counter.getValue()
                            + "***";
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

        System.out.println(
                "[Random Counter message] : If you see this message, the assert passed. The counter"
                    + " value is "
                        + counter.getValue());
    }
}
