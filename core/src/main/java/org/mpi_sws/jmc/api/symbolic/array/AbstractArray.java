package org.mpi_sws.jmc.api.symbolic.array;

/**
 * The {@link AbstractArray} class is used to represent an array value that can be either symbolic or concrete.
 */
public abstract class AbstractArray {

    /**
     * {@link #type} is used to store the type of the array.
     */
    public Type type;

    /**
     * Clones the abstract array.
     */
    public abstract AbstractArray clone();

    /**
     * Reads the value of the abstract array variable.
     *
     * @return the value of the abstract array variable.
     */
    public abstract AbstractArray read();

    /**
     * Writes the value to the abstract array variable with an abstract array value.
     *
     * @param value the value to be written.
     */
    public abstract void write(AbstractArray value);

    /**
     * Type of the array.
     */
    public enum Type {
        INT,
        BOOL,
        STRING,
        RATIONAL,
        REGEX
    }
}
