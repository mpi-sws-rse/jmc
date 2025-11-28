package org.mpi_sws.jmc.symbolic.array;

import org.mpi_sws.jmc.api.util.statements.JmcAssert;
import org.mpi_sws.jmc.symbolic.SymbolicFormula;
import org.mpi_sws.jmc.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.symbolic.bool.PropositionalFormula;
import org.mpi_sws.jmc.symbolic.integer.ArithmeticFormula;
import org.mpi_sws.jmc.symbolic.integer.SymbolicInteger;

/**
 * A simple array implementation that supports symbolic indexing by enumerating all possible
 * indices (this class will be replaced by a more efficient implementation in the future).
 */
public class EnumerationArray<T> {

    public T[] array;
    public int length;

    public EnumerationArray(int length) {
        this.length = length;
        this.array = (T[]) new Object[length];
    }

    public void set(int index, T value) {
        if (index >= 0 && index < length) {
            array[index] = value;
        } else {
            JmcAssert.check(false, "Symbolic array index out of bounds");
        }
    }

    public T get(int index) {
        if (index >= 0 && index < length) {
            return array[index];
        } else {
            JmcAssert.check(false, "Symbolic array index out of bounds");
            return null;
        }
    }

    public int length() {
        return length;
    }

    public T get(SymbolicInteger index) {
        ArithmeticFormula a = new ArithmeticFormula();
        JmcBooleanFormula op1 = a.geq(index, 0);
        JmcBooleanFormula op2 = a.lt(index, length);
        PropositionalFormula prop = new PropositionalFormula();
        JmcBooleanFormula op3 = prop.and(op1, op2);
        JmcAssert.check(op3, "Array index out of bounds");
        int i = enumerateIndex(0, index);
        return array[i];
    }

    private int enumerateIndex(int i, SymbolicInteger index) {
        ArithmeticFormula a = new ArithmeticFormula();
        JmcBooleanFormula op1 = a.eq(index, i);
        SymbolicFormula f = new SymbolicFormula();
        if (f.evaluate(op1)) {
            return i;
        } else {
            return enumerateIndex(i + 1, index);
        }
    }
}
