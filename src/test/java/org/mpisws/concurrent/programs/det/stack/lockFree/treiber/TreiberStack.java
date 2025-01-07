package org.mpisws.concurrent.programs.det.stack.lockFree.treiber;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.concurrent.programs.det.stack.lockFree.Node;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class TreiberStack<V> implements Stack<V> {

    private final AtomicReference<Node<V>> top = new AtomicReference<>(null);

    @Override
    public void push(V value) throws JMCInterruptException {
        Node<V> newNode = new Node<>(value);
        Node<V> oldTop;

        /*do {
            oldTop = top.get();
            newNode.next = oldTop;
        } while (!top.compareAndSet(oldTop, newNode));*/

        // Loop unrolled for one iteration
        oldTop = top.get();
        newNode.next = oldTop;
        top.compareAndSet(oldTop, newNode);
    }

    @Override
    public V pop() throws JMCInterruptException {
        Node<V> oldTop;
        Node<V> newTop;

        /*do {
            oldTop = top.get();
            if (oldTop == null) {
                return null;
            }
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop));*/

        // Loop unrolled for one iteration
        oldTop = top.get();
        if (oldTop == null) {
            return null;
        }
        newTop = oldTop.next;
        if (!top.compareAndSet(oldTop, newTop)) {
            return null;
        }

        return oldTop.value;
    }
}
