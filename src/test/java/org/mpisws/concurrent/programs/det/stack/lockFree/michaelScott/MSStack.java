package org.mpisws.concurrent.programs.det.stack.lockFree.michaelScott;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.concurrent.programs.det.stack.lockFree.Node;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class MSStack<V> implements Stack<V> {

    private final AtomicReference<Node<V>> top = new AtomicReference<>(null);

    @Override
    public void push(V value) throws JMCInterruptException {
        Node<V> newHead = new Node<>(value);
        while (true) {
            Node<V> oldTop = top.get();
            newHead.next = oldTop;
            if (top.compareAndSet(oldTop, newHead)) {
                return;
            }
        }
    }

    @Override
    public V pop() throws JMCInterruptException {
        while (true) {
            Node<V> oldTop = top.get();
            if (oldTop == null) {
                return null;
            }
            Node<V> newTop = oldTop.next;
            if (top.compareAndSet(oldTop, newTop)) {
                return oldTop.value;
            }
        }
    }
}
