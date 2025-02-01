package org.mpisws.concurrent.programs.nondet.stack.lockFree.elimination;

import org.mpisws.concurrent.programs.nondet.stack.DeletionThread;
import org.mpisws.concurrent.programs.nondet.stack.InsertionThread;
import org.mpisws.concurrent.programs.nondet.stack.lockFree.LockFreeStack;
import org.mpisws.concurrent.programs.nondet.stack.lockFree.Node;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class EliminationBackoffStack<V> extends LockFreeStack<V> {

    public final int capacity;
    public final EliminationArray<V> eliminationArray;

    public EliminationBackoffStack(int capacity) throws JMCInterruptException {
        this.capacity = capacity;
        eliminationArray = new EliminationArray<V>(capacity);
    }

    @Override
    public void push(V value) throws JMCInterruptException {
        Node<V> node = new Node<V>(value);
        InsertionThread thread = (InsertionThread) Thread.currentThread();
        /*while (true) {
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
        }*/
        // Unwinding the loop for one iteration

        if (tryPush(node)) {
            //System.out.println("Pushed value");
        } else {
            //System.out.println("Failed to push value");
            V otherValue = eliminationArray.visit(value, thread.index);
            if (otherValue == null) {
            }
        }
    }

    @Override
    public V pop() throws JMCInterruptException {
        DeletionThread thread = (DeletionThread) Thread.currentThread();
        /*while (true) {
            Node<V> returnNode = tryPop();
            if (returnNode != null) {
                return returnNode.value;
            } else {
                try {
                    //SymbolicInteger range = rangePolicy.get().getRange();
                    V otherValue = eliminationArray.visit(null);
                    if (otherValue != null) {
                        return otherValue;
                    }
                } catch (Exception ex) {
                    //System.out.println("Exception in pop, retrying");
                }
            }
        }*/
        // Unwinding the loop for one iteration
        Node<V> returnNode = tryPop();
        if (returnNode != null) {
            //System.out.println("Popped value");
            return returnNode.value;
        } else {
            V otherValue = eliminationArray.visit(null, thread.index);
            //System.out.println("Other value: " + otherValue);
            return otherValue;
        }
    }
}
