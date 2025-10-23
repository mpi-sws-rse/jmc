package org.mpi_sws.jmc.test.det.stack.lockFree;

import org.mpi_sws.jmc.test.det.stack.Stack;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<V> implements Stack<V> {

    public AtomicReference<Node<V>> top = new AtomicReference<>(null);

    protected boolean tryPush(Node<V> node) {
        Node<V> oldTop = top.get();
        node.next = oldTop;
        return top.compareAndSet(oldTop, node);
    }

    @Override
    public void push(V value) {
        Node<V> node = new Node<>(value);
        tryPush(node);
    }

    protected Node<V> tryPop() {
        Node<V> oldTop = top.get();
        if (oldTop == null) {
            return null;
        }
        Node<V> newTop = oldTop.next;
        if (top.compareAndSet(oldTop, newTop)) {
            return oldTop;
        } else {
            return null;
        }
    }

    @Override
    public V pop() {
        Node<V> returnNode = tryPop();
        if (returnNode != null) {
            return returnNode.value;
        } else {
            return null;
        }
    }
}
