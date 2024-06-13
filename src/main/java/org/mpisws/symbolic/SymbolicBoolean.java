package org.mpisws.symbolic;

public class SymbolicBoolean extends AbstractBoolean {
    private String name;
    private SymbolicOperation eval;
    private boolean isShared = false;

    public SymbolicBoolean(String name, boolean value, boolean isShared) {
        this.name = name;
        this.setValue(value);
        this.isShared = isShared;
    }

    public SymbolicBoolean(String name, boolean isShared) {
        this.setValue(false);
        this.name = name;
        this.isShared = isShared;
    }

    public void assign(SymbolicOperation expression) {
        SymbolicOperation expressionCopy = new SymbolicOperation();
        expressionCopy.setFormula(expression.getFormula());
        expressionCopy.setIntegerVariableMap(expression.getIntegerVariableMap());
        this.eval = expressionCopy;
    }

    public void assign(SymbolicBoolean symbolicBoolean) {
        if (symbolicBoolean.getEval() != null) {
            SymbolicOperation expressionCopy = new SymbolicOperation();
            expressionCopy.setFormula(symbolicBoolean.eval.getFormula());
            expressionCopy.setIntegerVariableMap(symbolicBoolean.eval.getIntegerVariableMap());
            this.eval = expressionCopy;
        } else {
            this.name = symbolicBoolean.getName();
        }
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
}
