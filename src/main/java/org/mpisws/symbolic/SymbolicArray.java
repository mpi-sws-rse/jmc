package org.mpisws.symbolic;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class SymbolicArray<T> {

    public T[] array;
    public int length;

    public SymbolicArray(int length) {
        this.length = length;
        this.array = (T[]) new Object[length];
    }

    public void set(int index, T value) throws JMCInterruptException {
        if (index >= 0 && index < length) {
            array[index] = value;
        } else {
            Utils.assertion(false, "Symbolic array index out of bounds");
        }
    }

    public T get(int index) throws JMCInterruptException {
        if (index >= 0 && index < length) {
            return array[index];
        } else {
            Utils.assertion(false, "Symbolic array index out of bounds");
            return null;
        }
    }

    public int length() {
        return length;
    }

    public T get(SymbolicInteger index) {
        ArithmeticFormula a = new ArithmeticFormula();
        SymbolicOperation op1 = a.geq(index, 0);
        SymbolicOperation op2 = a.lt(index, length);
        PropositionalFormula prop = new PropositionalFormula();
        SymbolicOperation op3 = prop.and(op1, op2);
        Utils.assertion(op3, "Array index out of bounds");
        int i = enumerateIndex(0, index);
        return array[i];
    }

    private int enumerateIndex(int i, SymbolicInteger index) {
        ArithmeticFormula a = new ArithmeticFormula();
        SymbolicOperation op1 = a.eq(index, i);
        SymbolicFormula f = new SymbolicFormula();
        if (f.evaluate(op1)) {
            return i;
        } else {
            return enumerateIndex(i + 1, index);
        }
    }

}
