package org.mpisws.symbolic;

import java.io.Serializable;

public class SymbolicInteger extends AbstractInteger implements Serializable {
    private String name;
    private ArithmeticStatement eval;
    private boolean isShared = false;

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
        this.eval = expression.deepCopy();
    }

    public void assign(SymbolicInteger symbolicInteger) {
        if (symbolicInteger.getEval() != null) {
            this.eval = symbolicInteger.getEval().deepCopy();
        } else {
            this.name = symbolicInteger.getName();
        }
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
}