package org.mpisws.symbolic;

import java.io.Serializable;
import org.mpisws.runtime.RuntimeEnvironment;

public class SymbolicInteger extends AbstractInteger implements Serializable {
  private String name;
  private ArithmeticStatement eval;
  private final boolean isShared;
  private int value;

  private SymbolicInteger() {
    String[] parts = this.toString().split("@");
    this.name = "SymbolicInteger@" + parts[parts.length - 1];
    this.isShared = false;
  }

  public SymbolicInteger(boolean isShared) {
    String[] parts = this.toString().split("@");
    this.name = "SymbolicInteger@" + parts[parts.length - 1];
    this.isShared = isShared;
    write();
  }

  public SymbolicInteger(boolean isShared, int hash) {
    String[] parts = this.toString().split("@");
    this.name = "SymbolicInteger@" + parts[parts.length - 1];
    this.isShared = isShared;
    this.setHash(hash);
    write();
  }

  private SymbolicInteger(String name, int value, boolean isShared) {
    this.name = name;
    this.setValue(value);
    this.isShared = isShared;
  }

  private SymbolicInteger(String name, boolean isShared) {
    this.name = name;
    this.isShared = isShared;
    write();
  }

  public void assign(ArithmeticStatement expression) {
    write(expression);
  }

  public void assign(SymbolicInteger symbolicInteger) {
    write(symbolicInteger);
  }

  public void print() {
    if (eval != null) {
      if (eval.getLeft() instanceof SymbolicInteger) {
        ((SymbolicInteger) eval.getLeft()).print();
      } else {
        System.out.print(" " + eval.getLeft().getValue() + " ");
      }
      System.out.print(" " + eval.getOperator() + " ");
      if (eval.getRight() instanceof SymbolicInteger) {
        ((SymbolicInteger) eval.getRight()).print();
      } else {
        System.out.print(" " + eval.getRight().getValue() + " ");
      }
    } else {
      System.out.print(" " + name + " ");
    }
  }

  @Override
  public SymbolicInteger deepCopy() {
    SymbolicInteger copy = new SymbolicInteger(name, getValue(), isShared);
    if (eval != null) {
      copy.setEval(eval.deepCopy());
    }
    return copy;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ArithmeticStatement getEval() {
    if (eval != null) {
      return eval;
    } else {
      return null;
    }
  }

  public void setEval(ArithmeticStatement eval) {
    this.eval = eval;
  }

  @Override
  public AbstractInteger read() {
    if (isShared) {
      RuntimeEnvironment.readOperation(
          this, Thread.currentThread(), "org.mpisws.symbolic.SymbolicInteger", "value", "SI");
      AbstractInteger copy = this.deepCopy();
      RuntimeEnvironment.waitRequest(Thread.currentThread());
      return copy;
    } else {
      return this.deepCopy();
    }
  }

  @Override
  public void write(AbstractInteger value) {
    SymbolicInteger symbolicInteger = (SymbolicInteger) value.read();

    if (isShared) {
      RuntimeEnvironment.writeOperation(
          this,
          symbolicInteger,
          Thread.currentThread(),
          "org.mpisws.symbolic.SymbolicInteger",
          "value",
          "SI");
    }

    if (symbolicInteger.getEval() != null) {
      this.eval = symbolicInteger.getEval().deepCopy();
    } else {
      this.name = symbolicInteger.getName();
    }

    if (isShared) {
      RuntimeEnvironment.waitRequest(Thread.currentThread());
    }
  }

  @Override
  public void write(ArithmeticStatement value) {
    if (isShared) {
      RuntimeEnvironment.writeOperation(
          this,
          value,
          Thread.currentThread(),
          "org.mpisws.symbolic.SymbolicInteger",
          "value",
          "SI");
    }

    this.eval = value.deepCopy();

    if (isShared) {
      RuntimeEnvironment.waitRequest(Thread.currentThread());
    }
  }

  private void write() {
    if (isShared) {
      RuntimeEnvironment.writeOperation(
          this,
          this.value,
          Thread.currentThread(),
          "org.mpisws.symbolic.SymbolicInteger",
          "value",
          "SI");
      RuntimeEnvironment.waitRequest(Thread.currentThread());
    }
  }

  public int getIntValue() {
    if (this.getEval() != null) {
      int leftValue;
      if (this.getEval().getLeft() instanceof SymbolicInteger left) {
        leftValue = left.getIntValue();
      } else {
        leftValue = this.getEval().getLeft().getValue();
      }
      int rightValue;
      if (this.getEval().getRight() instanceof SymbolicInteger right) {
        rightValue = right.getIntValue();
      } else {
        rightValue = this.getEval().getRight().getValue();
      }
      switch (this.getEval().getOperator()) {
        case ADD:
          return leftValue + rightValue;
        case SUB:
          return leftValue - rightValue;
        case MUL:
          return leftValue * rightValue;
        case DIV:
          if (rightValue == 0) {
            throw new ArithmeticException("[JMC Formula Message] Division by zero");
          }
          return leftValue / rightValue;
        case MOD:
          if (rightValue == 0) {
            throw new ArithmeticException("[JMC Formula Message] Modulo by zero");
          }
          return leftValue % rightValue;
        default:
          throw new IllegalArgumentException("[JMC Formula Message] Unsupported operator");
      }
    } else {
      return RuntimeEnvironment.solver.getSymIntVarValue(this.getName());
    }
  }
}
