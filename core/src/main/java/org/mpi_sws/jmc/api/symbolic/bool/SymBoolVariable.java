package org.mpi_sws.jmc.api.symbolic.bool;

import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.Random;

/**
 * Class representing a symbolic boolean variable with an associated formula and value.
 */
public class SymBoolVariable {

    /**
     * The BooleanFormula representing the symbolic variable
     */
    private BooleanFormula var;

    /**
     * The concrete boolean value associated with the symbolic variable
     */
    private boolean value;

    /**
     * Constructor to initialize the symbolic boolean variable with a formula.
     * The value is randomly assigned as true or false.
     *
     * @param var the BooleanFormula representing the symbolic variable
     */
    public SymBoolVariable(BooleanFormula var) {
        this.var = var;
        Random random = new Random();
        this.value = random.nextBoolean();
    }

    /**
     * Returns the BooleanFormula of the symbolic variable.
     *
     * @return the BooleanFormula representing the symbolic variable
     */
    public BooleanFormula getVar() {
        return var;
    }

    /**
     * Sets the BooleanFormula of the symbolic variable.
     *
     * @param var the BooleanFormula to set
     */
    public void setVar(BooleanFormula var) {
        this.var = var;
    }

    /**
     * Sets the concrete boolean value of the symbolic variable.
     *
     * @param value the boolean value to set
     */
    public void setValue(boolean value) {
        this.value = value;
    }

    /**
     * Returns the concrete boolean value of the symbolic variable.
     *
     * @return the boolean value associated with the symbolic variable
     */
    public boolean getValue() {
        return value;
    }

    /**
     * Creates a deep copy of the SymBoolVariable.
     *
     * @return a new instance of SymBoolVariable with the same formula and value
     */
    public SymBoolVariable clone() {
        SymBoolVariable copy = new SymBoolVariable(this.var);
        copy.setValue(this.value);
        return copy;
    }
}
