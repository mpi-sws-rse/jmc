package org.mpisws.concurrent.programs.nondet.stack.lockFree.elimination;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class EliminationArray<V> {

    public EnumerationArray<LockFreeExchanger<V>> exchanger;
    public int capacity;

    public EliminationArray(int capacity) throws JMCInterruptException {
        this.capacity = capacity;
        exchanger = new EnumerationArray<>(capacity);
        for (int i = 0; i < capacity; i++) {
            LockFreeExchanger<V> ex = new LockFreeExchanger<>();
            exchanger.set(i, ex);
        }
    }

    public V visit(V value, SymbolicInteger index) throws JMCInterruptException {
        ArithmeticFormula f = new ArithmeticFormula();
        SymbolicOperation op2 = f.geq(index, 0);
        SymbolicOperation op3 = f.lt(index, capacity);
        PropositionalFormula prop = new PropositionalFormula();
        SymbolicOperation op4 = prop.and(op2, op3);
        Utils.assume(op4); // assume 0 <= index < capacity
        return exchanger.get(index).exchange(value);
    }
}
