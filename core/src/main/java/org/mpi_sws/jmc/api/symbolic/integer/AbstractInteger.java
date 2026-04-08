package org.mpi_sws.jmc.api.symbolic.integer;

import org.mpi_sws.jmc.api.symbolic.SymbolicOperand;

/**
 * The {@link AbstractInteger} class is used to represent an integer value that can be either symbolic or concrete.
 */
public abstract class AbstractInteger implements SymbolicOperand {

    /**
     * {@link #concreteValue} is used to store the concrete value of the integer.
     */
    public int concreteValue;

    /**
     * Reads the value of the abstract integer variable.
     *
     * @return the value of the abstract integer variable.
     */
    public abstract AbstractInteger read();

    /**
     * Writes the value to the abstract integer variable with an abstract integer value.
     *
     * @param value the value to be written.
     */
    public abstract void write(AbstractInteger value);

    /**
     * Writes the value of the abstract integer variable with an arithmetic statement value.
     *
     * @param value the value to be written.
     */
    public abstract void write(ArithmeticStatement value);

    /**
     * {@link #concreteValue} is used to store the value of the integer.
     */
    public int getValue() {
        return this.concreteValue;
    }

    /**
     * Sets the concrete value.
     *
     * @param concreteValue the value to be set.
     */
    public void setValue(int concreteValue) {
        this.concreteValue = concreteValue;
    }

    /**
     * Makes a deep copy of the abstract integer.
     *
     * @return a deep copy of the abstract integer.
     */
    public abstract AbstractInteger clone();
}
