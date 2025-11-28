package org.mpi_sws.jmc.symbolic.bool;


public class ConcreteBoolean extends AbstractBoolean {

    public ConcreteBoolean() {
        this.setValue(false);
    }

    public ConcreteBoolean(boolean value) {
        this.setValue(value);
    }

    @Override
    public ConcreteBoolean clone() {
        return new ConcreteBoolean(this.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConcreteBoolean that = (ConcreteBoolean) o;
        return this.getValue() == that.getValue();
    }

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
