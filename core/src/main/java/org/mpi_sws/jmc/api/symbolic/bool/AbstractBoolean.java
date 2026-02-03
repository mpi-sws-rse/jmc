package org.mpi_sws.jmc.api.symbolic.bool;

import org.mpi_sws.jmc.api.symbolic.SymbolicOperand;

/**
 * The {@link AbstractBoolean} class is used to represent a boolean value that can be either symbolic or concrete.
 */
public abstract class AbstractBoolean implements SymbolicOperand {

    /**
     * {@link #value} is used to store the value of the boolean.
     */
    public boolean value;

    /**
     * Makes a deep copy of the boolean.
     *
     * @return a deep copy of the boolean.
     */
    public abstract AbstractBoolean clone();

    /**
     * Reads the value of the abstract boolean variable.
     *
     * @return the value of the abstract boolean variable.
     */
    public abstract AbstractBoolean read();

    /**
     * Writes the value of the abstract boolean variable with an abstract boolean value.
     *
     * @param value the value to be written.
     */
    public abstract void write(SymbolicBoolean value);

    /**
     * Writes the value of the abstract boolean variable with a boolean formula value.
     *
     * @param value the value to be written.
     */
    public abstract void write(JmcBooleanFormula value);

    /**
     * Gets the value of the boolean.
     *
     * @return the value of the boolean.
     */
    public boolean getValue() {
        return value;
    }

    /**
     * Sets the value of the boolean.
     *
     * @param value the value to be set.
     */
    public void setValue(boolean value) {
        this.value = value;
    }
}
