package org.mpisws.symbolic;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.Random;

public class SymbolicBoolean extends AbstractBoolean {
    private String name;
    private SymbolicOperation eval;
    private boolean isShared = false;
    private boolean value;

    private SymbolicBoolean(boolean isShared) {
        String[] parts = this.toString().split("@");
        this.name = "SymbolicBoolean@" + parts[parts.length - 1];
        this.isShared = isShared;
        write();
    }

    private SymbolicBoolean(String name, boolean value, boolean isShared) {
        long id = RuntimeEnvironment.threadIdMap.get(Thread.currentThread().getId());
        this.name = name;
        this.setValue(value);
        this.isShared = isShared;
    }

    public SymbolicBoolean(String name, boolean isShared) {
        long id = RuntimeEnvironment.threadIdMap.get(Thread.currentThread().getId());
        this.name = "SymbolicBoolean@" + name + "_" + id;
        this.isShared = isShared;
        this.write();
    }

    public void assign(SymbolicOperation expression) {
        write(expression);
    }

    public void assign(SymbolicBoolean symbolicBoolean) {
        write(symbolicBoolean);
    }

    public void print() {
        if (eval != null) {
            System.out.print(" " + eval.getFormula() + " ");
        } else {
            System.out.print(" " + name + " ");
        }
    }

    @Override
    public SymbolicBoolean deepCopy() {
        SymbolicBoolean copy = new SymbolicBoolean(name, getValue(), isShared);
        if (eval != null) {
            SymbolicOperation expressionCopy = new SymbolicOperation();
            expressionCopy.setFormula(eval.getFormula());
            expressionCopy.setIntegerVariableMap(eval.getIntegerVariableMap());
            copy.setEval(expressionCopy);
        }
        return copy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SymbolicOperation getEval() {
        return eval;
    }

    public void setEval(SymbolicOperation eval) {
        this.eval = eval;
    }

    @Override
    public AbstractBoolean read() {
        if (this.isShared) {
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org.mpisws.symbolic.SymbolicBoolean", "value", "SZ");
            AbstractBoolean copy = this.deepCopy();
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return copy;
        } else {
            return this.deepCopy();
        }
    }

    @Override
    public void write(SymbolicBoolean value) {
        SymbolicBoolean symbolicBoolean = (SymbolicBoolean) value.read();

        if (isShared) {
            RuntimeEnvironment.writeOperation(this, symbolicBoolean, Thread.currentThread(), "org.mpisws.symbolic.SymbolicBoolean", "value", "SZ");
        }

        if (symbolicBoolean.getEval() != null) {
            SymbolicOperation expressionCopy = new SymbolicOperation();
            expressionCopy.setFormula(symbolicBoolean.eval.getFormula());
            expressionCopy.setIntegerVariableMap(symbolicBoolean.eval.getIntegerVariableMap());
            this.eval = expressionCopy;
        } else {
            this.name = symbolicBoolean.getName();
        }

        if (isShared) {
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        }
    }

    @Override
    public void write(SymbolicOperation value) {

        if (isShared) {
            RuntimeEnvironment.writeOperation(this, value, Thread.currentThread(), "org.mpisws.symbolic.SymbolicBoolean", "value", "SZ");
        }

        SymbolicOperation expressionCopy = new SymbolicOperation();
        expressionCopy.setFormula(value.getFormula());
        expressionCopy.setIntegerVariableMap(value.getIntegerVariableMap());
        this.eval = expressionCopy;

        if (isShared) {
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        }
    }

    private void write() {
        if (isShared) {
            RuntimeEnvironment.writeOperation(this, value, Thread.currentThread(), "org.mpisws.symbolic.SymbolicBoolean", "value", "SZ");
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        }
    }
}
