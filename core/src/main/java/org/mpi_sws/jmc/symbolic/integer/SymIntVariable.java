package org.mpi_sws.jmc.symbolic.integer;

import org.sosy_lab.java_smt.api.NumeralFormula;

import java.util.Random;

public class SymIntVariable {

    private NumeralFormula.IntegerFormula var;

    public int value;

    public SymIntVariable(NumeralFormula.IntegerFormula var) {
        this.var = var;
        Random random = new Random();
        this.value = random.nextInt();
    }

    public NumeralFormula.IntegerFormula getVar() {
        return var;
    }

    public void setVar(NumeralFormula.IntegerFormula var) {
        this.var = var;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public SymIntVariable clone() {
        SymIntVariable copy = new SymIntVariable(this.var);
        copy.setValue(this.value);
        return copy;
    }
}
