package org.mpisws.jmc.test.det.stack.lockFree.elimination;

import org.mpisws.jmc.test.det.stack.lockFree.LockFreeStack;
import org.mpisws.jmc.test.det.stack.lockFree.Node;

import java.util.concurrent.TimeoutException;

public class EliminationBackoffStack<V> extends LockFreeStack<V> {

    public final int capacity = 1;
    public EliminationArray<V> eliminationArray = new EliminationArray<V>(capacity);

    @Override
    public void push(V value) {
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
    public V pop() {
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
