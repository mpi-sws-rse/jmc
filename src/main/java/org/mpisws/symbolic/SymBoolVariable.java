package org.mpisws.symbolic;

import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.Random;

public class SymBoolVariable {

    private BooleanFormula var;

    private boolean value;

    public SymBoolVariable(BooleanFormula var) {
        this.var = var;
//        Random random = new Random();
//        this.value = random.nextBoolean();
        value = true;
    }

    public BooleanFormula getVar() {
        return var;
    }

    public void setVar(BooleanFormula var) {
        this.var = var;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }
}