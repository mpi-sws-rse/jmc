package org.mpi_sws.jmc.api.symbolic.integer;

import org.sosy_lab.java_smt.api.NumeralFormula;

import java.util.Random;


public class SymIntVariable {

    /**
     * The symbolic variable representing an integer
     */
    private NumeralFormula.IntegerFormula var;

    /**
     * The concrete value assigned to the symbolic integer variable
     */
    public int value;

    /**
     * Constructor to create a symbolic integer variable with a given symbolic formula.
     *
     * @param var the symbolic integer formula
     */
    public SymIntVariable(NumeralFormula.IntegerFormula var) {
        this.var = var;
        Random random = new Random();
        this.value = random.nextInt();
    }

    /**
     * Gets the symbolic integer formula.
     *
     * @return the symbolic integer formula
     */
    public NumeralFormula.IntegerFormula getVar() {
        return var;
    }

    /**
     * Sets the symbolic integer formula.
     *
     * @param var the symbolic integer formula to set
     */
    public void setVar(NumeralFormula.IntegerFormula var) {
        this.var = var;
    }

    /**
     * Sets the concrete value assigned to the symbolic integer variable.
     *
     * @param value the concrete value to set
     */
    public void setValue(int value) {
        this.value = value;
    }

    /**
     * Gets the concrete value assigned to the symbolic integer variable.
     *
     * @return the concrete value
     */
    public int getValue() {
        return value;
    }

    /**
     * Creates a deep copy of the SymIntVariable.
     *
     * @return a deep copy of the SymIntVariable
     */
    public SymIntVariable clone() {
        SymIntVariable copy = new SymIntVariable(this.var);
        copy.setValue(this.value);
        return copy;
    }
}
