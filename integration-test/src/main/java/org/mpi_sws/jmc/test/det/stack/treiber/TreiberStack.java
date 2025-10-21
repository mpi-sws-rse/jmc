package org.mpi_sws.jmc.test.det.stack.treiber;

import org.mpi_sws.jmc.test.det.stack.Node;
import org.mpi_sws.jmc.test.det.stack.Stack;

import java.util.concurrent.atomic.AtomicReference;

public class TreiberStack<V> implements Stack<V> {

    private final AtomicReference<Node<V>> top = new AtomicReference<>(null);

    @Override
    public void push(V value) {
        Node<V> newNode = new Node<>(value);
        Node<V> oldTop;

        // Loop unrolled for one iteration
        oldTop = top.get();
        newNode.next = oldTop;
        top.compareAndSet(oldTop, newNode);
    }

    @Override
    public V pop() {
        Node<V> oldTop;
        Node<V> newTop;

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
