package org.mpisws.symbolic;

import java.io.Serializable;

public class ConcreteBoolean extends AbstractBoolean implements Serializable {

  public ConcreteBoolean() {
    this.setValue(false);
  }

  public ConcreteBoolean(boolean value) {
    this.setValue(value);
  }

  @Override
  public ConcreteBoolean deepCopy() {
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
  public int hashCode() {
    return java.util.Objects.hash(this.getValue());
  }

  @Override
  public AbstractBoolean read() {
    AbstractBoolean copy = new ConcreteBoolean(this.getValue());
    return copy;
  }

  @Override
  public void write(SymbolicOperation value) {
    // Do nothing
  }

  @Override
  public void write(SymbolicBoolean value) {
    // Do nothing
  }
}
