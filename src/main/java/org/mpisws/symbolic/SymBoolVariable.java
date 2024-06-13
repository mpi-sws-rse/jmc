package org.mpisws.symbolic;

import org.sosy_lab.java_smt.api.BooleanFormula;

public class SymBoolVariable {

    private BooleanFormula var;

    public SymBoolVariable(BooleanFormula var) {
        this.var = var;
    }

    public BooleanFormula getVar() {
        return var;
    }

    public void setVar(BooleanFormula var) {
        this.var = var;
    }
}
