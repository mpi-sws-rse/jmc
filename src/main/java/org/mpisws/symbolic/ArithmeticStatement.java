package org.mpisws.symbolic;

import java.io.Serializable;

public class ArithmeticStatement implements Serializable {
  private AbstractInteger left;
  private AbstractInteger right;
  private InstructionType operator;

  public void add(AbstractInteger var1, AbstractInteger var2) {
    this.left = var1.read();
    this.right = var2.read();
    this.operator = InstructionType.ADD;
  }

  public void add(AbstractInteger var1, int var2) {
    this.left = var1.read();
    this.right = new ConcreteInteger(var2);
    this.operator = InstructionType.ADD;
  }

  public void add(int var1, AbstractInteger var2) {
    this.left = new ConcreteInteger(var1);
    this.right = var2.read();
    this.operator = InstructionType.ADD;
  }

  public void sub(AbstractInteger var1, AbstractInteger var2) {
    this.left = var1.read();
    this.right = var2.read();
    this.operator = InstructionType.SUB;
  }

  public void sub(AbstractInteger var1, int var2) {
    this.left = var1.read();
    this.right = new ConcreteInteger(var2);
    this.operator = InstructionType.SUB;
  }

  public void sub(int var1, AbstractInteger var2) {
    this.left = new ConcreteInteger(var1);
    this.right = var2.read();
    this.operator = InstructionType.SUB;
  }

  public void mul(AbstractInteger var1, AbstractInteger var2) {
    this.left = var1.read();
    this.right = var2.read();
    this.operator = InstructionType.MUL;
  }

  public void mul(AbstractInteger var1, int var2) {
    this.left = var1.read();
    this.right = new ConcreteInteger(var2);
    this.operator = InstructionType.MUL;
  }

  public void mul(int var1, AbstractInteger var2) {
    this.left = new ConcreteInteger(var1);
    this.right = var2.read();
    this.operator = InstructionType.MUL;
  }

  public void div(AbstractInteger var1, AbstractInteger var2) {
    this.left = var1.read();
    this.right = var2.read();
    this.operator = InstructionType.DIV;
  }

  public void div(AbstractInteger var1, int var2) {
    this.left = var1.read();
    this.right = new ConcreteInteger(var2);
    this.operator = InstructionType.DIV;
  }

  public void div(int var1, AbstractInteger var2) {
    this.left = new ConcreteInteger(var1);
    this.right = var2.read();
    this.operator = InstructionType.DIV;
  }

  public void mod(AbstractInteger var1, AbstractInteger var2) {
    this.left = var1.read();
    this.right = var2.read();
    this.operator = InstructionType.MOD;
  }

  public void mod(AbstractInteger var1, int var2) {
    this.left = var1.read();
    this.right = new ConcreteInteger(var2);
    this.operator = InstructionType.MOD;
  }

  public void mod(int var1, AbstractInteger var2) {
    this.left = new ConcreteInteger(var1);
    this.right = var2.read();
    this.operator = InstructionType.MOD;
  }

  public ArithmeticStatement deepCopy() {
    ArithmeticStatement copy = new ArithmeticStatement();
    copy.left = left.deepCopy();
    copy.right = right.deepCopy();
    copy.operator = operator;
    return copy;
  }

  public AbstractInteger getLeft() {
    return this.left;
  }

  public AbstractInteger getRight() {
    return this.right;
  }

  public InstructionType getOperator() {
    return this.operator;
  }

  public void setLeft(AbstractInteger left) {
    this.left = left;
  }

  public void setRight(AbstractInteger right) {
    this.right = right;
  }

  public void setOperator(InstructionType operator) {
    this.operator = operator;
  }
}
