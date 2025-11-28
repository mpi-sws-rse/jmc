package org.mpi_sws.jmc.symbolic.integer;

/**
 * Concrete implementation of AbstractInteger.
 * This class represents a concrete integer value.
 */
public class ConcreteInteger extends AbstractInteger {

    /**
     * Default constructor initializes the value to 0.
     */
    public ConcreteInteger() {
        this.setValue(0);
    }

    /**
     * Constructor that initializes the value to the given integer.
     *
     * @param value the integer value to set
     */
    public ConcreteInteger(int value) {
        this.setValue(value);
    }

    /**
     * Creates a deep copy of this ConcreteInteger.
     *
     * @return a new instance of ConcreteInteger with the same value
     */
    @Override
    public ConcreteInteger clone() {
        return new ConcreteInteger(this.getValue());
    }

    /**
     * Checks if this ConcreteInteger is equal to another object.
     *
     * @param o the object to compare with
     * @return true if the object is a ConcreteInteger with the same value, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConcreteInteger that = (ConcreteInteger) o;
        return this.getValue() == that.getValue();
    }

    /**
     * Reads the value of this ConcreteInteger.
     *
     * @return a new instance of ConcreteInteger with the same value
     */
    @Override
    public AbstractInteger read() {
        return new ConcreteInteger(this.getValue());
    }

    /**
     * Writes the value of this ConcreteInteger.
     * This method does nothing in this implementation.
     *
     * @param value the AbstractInteger value to write
     */
    @Override
    public void write(AbstractInteger value) {
        // Do nothing
    }

    /**
     * Writes the value of this ConcreteInteger.
     * This method does nothing in this implementation.
     *
     * @param value the ArithmeticStatement value to write
     */
    @Override
    public void write(ArithmeticStatement value) {
        // Do nothing
    }
}
