package org.mpisws.symbolic;

import java.io.Serializable;

/**
 * The {@link AbstractInteger} class is used to represent an integer value that can be either symbolic or concrete.
 */
public abstract class AbstractInteger implements Serializable, SymbolicOperand {

    /**
     * @property {@link #value} is used to store the value of the integer.
     */
    public int value;

    /**
     * Makes a deep copy of the integer.
     *
     * @return a deep copy of the integer.
     */
    abstract AbstractInteger deepCopy();

    /**
     * Reads the value of the abstract integer variable.
     *
     * @return the value of the abstract integer variable.
     */
    public abstract AbstractInteger read();

    /**
     * Writes the value of the abstract integer variable with an abstract integer value.
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

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
