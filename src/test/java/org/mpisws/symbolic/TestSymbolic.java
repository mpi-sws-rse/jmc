package org.mpisws.symbolic;

import symbolic.SymbolicInteger;
import symbolic.ArithmeticStatement;
import symbolic.ArithmeticFormula;

public class TestSymbolic {

    public static void main(String[] args) {

        SymbolicInteger a = new SymbolicInteger("a");
        SymbolicInteger b = new SymbolicInteger("b");
        ArithmeticStatement stmt = new ArithmeticStatement();
        stmt.add(a, b);
        a.assign(stmt);

        ArithmeticStatement stmt2 = new ArithmeticStatement();
        stmt2.add(b, 1);
        b.assign(stmt2);

        ArithmeticStatement stmt3 = new ArithmeticStatement();
        stmt3.add(a, 1);
        a.assign(stmt3);

        ArithmeticStatement stmt4 = new ArithmeticStatement();
        stmt4.mul(b, 3);
        b.assign(stmt4);

        System.out.print("a = ");
        a.print();
        System.out.println();
        System.out.print("b = ");
        b.print();

        ArithmeticFormula formula = new ArithmeticFormula();
        if (formula.eq(a, b)) {
            System.out.println("a == b");
        } else {
            System.out.println("a != b");
        }

        System.out.println(formula.getIntegerVariableMap());
    }
}
