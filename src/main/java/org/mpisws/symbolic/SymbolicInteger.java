package org.mpisws.symbolic;

import org.mpisws.runtime.RuntimeEnvironment;

import java.io.Serializable;

public class SymbolicInteger extends AbstractInteger implements Serializable {
    private String name;
    private ArithmeticStatement eval;
    private boolean isShared = false;
    private final int value = 0;

    public SymbolicInteger(String name, int value, boolean isShared) {
        this.name = name;
        this.setValue(value);
        this.isShared = isShared;
    }

    public SymbolicInteger(String name, boolean isShared) {
        this.setValue(0);
        this.name = name;
        this.isShared = isShared;
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
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org.mpisws.symbolic.SymbolicInteger", "value", "SI");
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
            RuntimeEnvironment.writeOperation(this, symbolicInteger, Thread.currentThread(), "org.mpisws.symbolic.SymbolicInteger", "value", "SI");
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
            RuntimeEnvironment.writeOperation(this, value, Thread.currentThread(), "org.mpisws.symbolic.SymbolicInteger", "value", "SI");
        }

        this.eval = value.deepCopy();

        if (isShared) {
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        }
    }
}