package org.mpisws.concurrent.programs.det.stack.lockFree.elimination;

import org.mpisws.concurrent.programs.det.stack.DeletionThread;
import org.mpisws.concurrent.programs.det.stack.InsertionThread;
import org.mpisws.concurrent.programs.det.stack.lockFree.LockFreeStack;
import org.mpisws.concurrent.programs.det.stack.lockFree.Node;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.concurrent.TimeoutException;

public class EliminationBackoffStack<V> extends LockFreeStack<V> {

    public final int capacity;
    public final EliminationArray<V> eliminationArray;

    public EliminationBackoffStack(int capacity) {
        this.capacity = capacity;
        eliminationArray = new EliminationArray<V>(capacity);
    }

    @Override
    public void push(V value) throws JMCInterruptException {
        Node<V> node = new Node<V>(value);
        InsertionThread thread = (InsertionThread) Thread.currentThread();
        int index = thread.index;
        /*while (true) {
            if (tryPush(node)) {
                return;
            } else {
                try {
                    V otherValue = eliminationArray.visit(value, capacity);
                    if (otherValue == null) {
                        return;
                    }
                } catch (TimeoutException ex) {
                    System.out.println("TimeoutException in push, retrying");
                }
            }
        }*/
        // Unwinding the loop for one iteration
        if (tryPush(node)) {
        } else {
            V otherValue = eliminationArray.visit(value, index);
            if (otherValue == null) {
            }
        }
    }

    @Override
    public V pop() throws JMCInterruptException {
        DeletionThread thread = (DeletionThread) Thread.currentThread();
        int index = thread.index;
        /*while (true) {
            Node<V> returnNode = tryPop();
            if (returnNode != null) {
                return returnNode.value;
            } else {
                try {
                    V otherValue = eliminationArray.visit(null, capacity);
                    if (otherValue != null) {
                        return otherValue;
                    }
                } catch (TimeoutException ex) {
                    System.out.println("TimeoutException in pop, retrying");
                }
            }
        }*/
        // Unwinding the loop for one iteration
        Node<V> returnNode = tryPop();
        if (returnNode != null) {
            return returnNode.value;
        } else {
            V otherValue = eliminationArray.visit(null, index);
            return otherValue;
        }
    }
}
