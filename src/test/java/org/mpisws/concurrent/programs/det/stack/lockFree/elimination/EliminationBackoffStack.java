package org.mpisws.concurrent.programs.det.stack.lockFree.elimination;

import org.mpisws.concurrent.programs.det.stack.lockFree.LockFreeStack;
import org.mpisws.concurrent.programs.det.stack.lockFree.Node;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.concurrent.TimeoutException;

public class EliminationBackoffStack<V> extends LockFreeStack<V> {

    public final int capacity = 2;
    public EliminationArray<V> eliminationArray = new EliminationArray<V>(capacity);

    @Override
    public void push(V value) throws JMCInterruptException {
        Node<V> node = new Node<V>(value);
        while (true) {
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
                    V otherValue = eliminationArray.visit(null, capacity);
                    if (otherValue != null) {
                        return otherValue;
                    }
                } catch (TimeoutException ex) {
                    System.out.println("TimeoutException in pop, retrying");
                }
            }
        }
    }
}