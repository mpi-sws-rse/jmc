package org.mpisws.symbolic;

import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

public class SymIntVariable {

    private IntegerFormula var;

    public SymIntVariable(IntegerFormula var) {
        this.var = var;
    }

    public IntegerFormula getVar() {
        return var;
    }

    public void setVar(IntegerFormula var) {
        this.var = var;
    }
}
