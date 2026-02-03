package org.mpi_sws.jmc.api.symbolic.array;

import org.mpi_sws.jmc.api.util.statements.JmcAssert;
import org.mpi_sws.jmc.api.symbolic.SymbolicFormula;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.PropositionalFormula;
import org.mpi_sws.jmc.api.symbolic.integer.ArithmeticFormula;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;

/**
 * A simple array implementation that supports symbolic indexing by enumerating all possible
 * indices (this class will be replaced by a more efficient implementation in the future).
 */
public class EnumerationArray<T> {

    /**
     * The core underlying array.
     */
    public T[] array;

    /**
     * Creates a new {@link EnumerationArray} with the given length.
     *
     * @param length the length of the array.
     */
    public EnumerationArray(int length) {
        this.array = (T[]) new Object[length];
    }

    /**
     * Sets the value at the given index.
     *
     * @param index the index to set the value at.
     * @param value the value to set.
     */
    public void set(int index, T value) {
        if (index >= 0 && index < array.length) {
            array[index] = value;
        } else {
            JmcAssert.check(false, "Symbolic array index out of bounds");
        }
    }

    /**
     * Gets the value at the given index.
     *
     * @param index the index to get the value from.
     * @return the value at the given index.
     */
    public T get(int index) {
        if (index >= 0 && index < array.length) {
            return array[index];
        } else {
            JmcAssert.check(false, "Symbolic array index out of bounds");
            return null;
        }
    }

    /**
     * Gets the length of the array.
     *
     * @return the length of the array.
     */
    public int length() {
        return array.length;
    }

    /**
     * Gets the value at the given symbolic index.
     *
     * @param index the symbolic index to get the value from.
     * @return the value at the given symbolic index.
     */
    public T get(SymbolicInteger index) {
        ArithmeticFormula a = new ArithmeticFormula();
        JmcBooleanFormula op1 = a.geq(index, 0);
        JmcBooleanFormula op2 = a.lt(index, array.length);
        PropositionalFormula prop = new PropositionalFormula();
        JmcBooleanFormula op3 = prop.and(op1, op2);
        JmcAssert.check(op3, "Array index out of bounds");
        int i = enumerateIndex(0, index);
        return array[i];
    }

    /**
     * Enumerates the index to find the concrete value of the symbolic index.
     *
     * @param i     the current index to check.
     * @param index the symbolic index.
     * @return the concrete value of the symbolic index.
     */
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
