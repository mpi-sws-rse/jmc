package org.mpisws.jmc.programs.det.stack.lockFree;

import org.mpisws.jmc.programs.det.stack.Backoff;
import org.mpisws.jmc.programs.det.stack.Stack;
import org.mpisws.jmc.api.util.concurrent.JmcAtomicReference;

public class LockFreeStack<V> implements Stack<V> {

    public final int MIN_DELAY = 1;
    public final int MAX_DELAY = 10;
    public JmcAtomicReference<Node<V>> top = new JmcAtomicReference<>(null);
    public Backoff backoff = new Backoff(MIN_DELAY, MAX_DELAY);

    protected boolean tryPush(Node<V> node) {
        Node<V> oldTop = top.get();
        node.next = oldTop;
        return top.compareAndSet(oldTop, node);
    }

    @Override
    public void push(V value) {
        Node<V> node = new Node<>(value);
        while (!tryPush(node)) {
            backoff.backoff();
        }
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
        while (true) {
            Node<V> returnNode = tryPop();
            if (returnNode != null) {
                return returnNode.value;
            } else {
                backoff.backoff();
            }
        }
    }
}
