package org.mpisws.concurrent.programs.nondet.stack.lockFree.elimination;

import org.mpisws.concurrent.programs.nondet.stack.lockFree.LockFreeStack;
import org.mpisws.concurrent.programs.nondet.stack.lockFree.Node;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class EliminationBackoffStack<V> extends LockFreeStack<V> {

    public final SymbolicInteger capacity = new SymbolicInteger("capacity", false);
    public EliminationArray<V> eliminationArray = new EliminationArray<V>(capacity);
    public ThreadLocal<RangePolicy> rangePolicy = new ThreadLocal<>();

    public EliminationBackoffStack() throws JMCInterruptException {
        rangePolicy.set(new RangePolicy());
    }

    @Override
    public void push(V value) throws JMCInterruptException {
        Node<V> node = new Node<V>(value);
        while (true) {
            if (tryPush(node)) {
                return;
            } else {
                try {
                    SymbolicInteger range = rangePolicy.get().getRange();
                    V otherValue = eliminationArray.visit(value, range);
                    if (otherValue == null) {
                        return;
                    }
                } catch (Exception ex) {
                    System.out.println("Exception in push, retrying");
                }
            }
        }
    }

    @Override
    public V pop() throws JMCInterruptException {
        while (true) {
            Node<V> returnNode = tryPop();
            if (returnNode != null) {
                return returnNode.value;
            } else {
                try {
                    SymbolicInteger range = rangePolicy.get().getRange();
                    V otherValue = eliminationArray.visit(null, range);
                    if (otherValue != null) {
                        return otherValue;
                    }
                } catch (Exception ex) {
                    System.out.println("Exception in pop, retrying");
                }
            }
        }
    }
}
