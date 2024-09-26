package org.mpisws.concurrent.programs.symbolic.gcd;

import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.ArithmeticStatement;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class ParallelGCD {

    public static void main(String[] args) {
        try {
            SymbolicInteger a_in = new SymbolicInteger(false);
            SymbolicInteger b_in = new SymbolicInteger(false);

            ArithmeticFormula formula = new ArithmeticFormula();

            SymbolicOperation op = formula.gt(a_in, 0); // a_in > 0
            Utils.assume(op);

            op = formula.leq(a_in, 2); // a_in <= 2
            Utils.assume(op);

            op = formula.gt(b_in, 0); // b_in > 0
            Utils.assume(op);

            op = formula.leq(b_in, 2); // b_in <= 2
            Utils.assume(op);

            Object lock = new Object();
            SymbolicInteger a = new SymbolicInteger(true);
            a.assign(a_in);
            SymbolicInteger b = new SymbolicInteger(true);
            b.assign(b_in);
            Decrementor dec1 = new Decrementor(a, b, lock);
            Decrementor dec2 = new Decrementor(b, a, lock);

            dec1.start();
            dec2.start();

            dec1.join();
            dec2.join();

            System.out.println("GCD is computed");

            SymbolicInteger guessedGCD = new SymbolicInteger(false);

            op = formula.gt(guessedGCD, 1); // guessedGCD > 1
            Utils.assume(op);

            ArithmeticStatement stmt1 = new ArithmeticStatement();
            stmt1.mod(a_in, guessedGCD);
            SymbolicInteger mod1 = new SymbolicInteger(false);
            mod1.assign(stmt1); // a_in % guessedGCD

            ArithmeticStatement stmt2 = new ArithmeticStatement();
            stmt2.mod(b_in, guessedGCD);
            SymbolicInteger mod2 = new SymbolicInteger(false);
            mod2.assign(stmt2); // b_in % guessedGCD

            op = formula.eq(mod1, 0);
            Utils.assume(op); // a_in % guessedGCD == 0

            op = formula.eq(mod2, 0);
            Utils.assume(op); // b_in % guessedGCD == 0

            ArithmeticStatement stmt3 = new ArithmeticStatement();
            stmt3.mod(a_in, a);
            SymbolicInteger mod3 = new SymbolicInteger(false);
            mod3.assign(stmt3); // a_in % gcd : a = gcd

            ArithmeticStatement stmt4 = new ArithmeticStatement();
            stmt4.mod(b_in, a);
            SymbolicInteger mod4 = new SymbolicInteger(false);
            mod4.assign(stmt4); // b_in % gcd : a = gcd

            op = formula.eq(mod3, 0);
            Utils.assertion(op, "a_in % gcd != 0"); // a_in % gcd == 0
            System.out.println("If you see this message, the assert a_in % gcd == 0 passed.");

            op = formula.eq(mod4, 0);
            Utils.assertion(op, "b_in % gcd != 0"); // b_in % gcd == 0
            System.out.println("If you see this message, the assert b_in % gcd == 0 passed.");

            op = formula.geq(a, guessedGCD);
            Utils.assertion(op, "a < gcd"); // a >= gcd
            System.out.println("If you see this message, the assert a >= gcd passed.");
        } catch (JMCInterruptException e) {

        } catch (InterruptedException e) {

        }

    }
}
