package org.mpisws.symbolic;

import java.io.Serializable;

public class ConcreteInteger extends AbstractInteger implements Serializable {

    public ConcreteInteger() {
        this.setValue(0);
    }

    public ConcreteInteger(int value) {
        this.setValue(value);
    }

    @Override
    public ConcreteInteger deepCopy() {
        return new ConcreteInteger(this.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConcreteInteger that = (ConcreteInteger) o;
        return this.getValue() == that.getValue();
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.getValue());
    }
}