package org.mpisws.symbolic;

import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

import java.util.Random;

public class SymIntVariable {

    private IntegerFormula var;

    public int value;

    public SymIntVariable(IntegerFormula var) {
        this.var = var;
//        Random random = new Random();
//        this.value = random.nextInt();
        this.value = 1;
    }

    public IntegerFormula getVar() {
        return var;
    }

    public void setVar(IntegerFormula var) {
        this.var = var;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public SymIntVariable deepCopy() {
        SymIntVariable copy = new SymIntVariable(this.var);
        copy.setValue(this.value);
        return copy;
    }
}
