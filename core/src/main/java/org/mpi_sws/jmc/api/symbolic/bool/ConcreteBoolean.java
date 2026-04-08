package org.mpi_sws.jmc.api.symbolic.bool;

/**
 * The {@link ConcreteBoolean} class is used to represent a concrete boolean value.
 */
public class ConcreteBoolean extends AbstractBoolean {

    /**
     * Default constructor that initializes the boolean value to false.
     */
    public ConcreteBoolean() {
        this.setValue(false);
    }

    /**
     * Constructor that initializes the boolean value to the specified value.
     *
     * @param value the boolean value to be set.
     */
    public ConcreteBoolean(boolean value) {
        this.setValue(value);
    }

    /**
     * Creates a deep copy of this ConcreteBoolean.
     *
     * @return a new instance of ConcreteBoolean with the same value.
     */
    @Override
    public ConcreteBoolean clone() {
        return new ConcreteBoolean(this.getValue());
    }

    /**
     * Checks if this ConcreteBoolean is equal to another object.
     *
     * @param o the object to compare with.
     * @return true if the object is a ConcreteBoolean with the same value, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConcreteBoolean that = (ConcreteBoolean) o;
        return this.getValue() == that.getValue();
    }

    /**
     * Reads the value of this ConcreteBoolean.
     *
     * @return a new instance of ConcreteBoolean with the same value.
     */
    @Override
    public AbstractBoolean read() {
        AbstractBoolean copy = new ConcreteBoolean(this.getValue());
        return copy;
    }

    @Override
    public void write(JmcBooleanFormula value) {
        // Do nothing
    }

    @Override
    public void write(SymbolicBoolean value) {
        // Do nothing
    }
}
