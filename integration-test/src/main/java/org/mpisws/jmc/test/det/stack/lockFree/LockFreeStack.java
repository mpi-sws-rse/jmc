package org.mpisws.jmc.test.det.stack.lockFree;

import org.mpisws.jmc.test.det.stack.Backoff;
import org.mpisws.jmc.test.det.stack.Stack;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<V> implements Stack<V> {

    //public final int MIN_DELAY = 1;
    //public final int MAX_DELAY = 10;
    public AtomicReference<Node<V>> top = new AtomicReference<>(null);
    //public Backoff backoff = new Backoff(MIN_DELAY, MAX_DELAY);

    protected boolean tryPush(Node<V> node) {
        Node<V> oldTop = top.get();
        node.next = oldTop;
        return top.compareAndSet(oldTop, node);
    }

    @Override
    public void push(V value) {
        Node<V> node = new Node<>(value);
        /*while (!tryPush(node)) {
            backoff.backoff();
        }*/
        // Unwinding the loop for one iteration
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
        /*while (true) {
            Node<V> returnNode = tryPop();
            if (returnNode != null) {
                return returnNode.value;
            } else {
                //backoff.backoff();
            }
        }*/
        // Unwinding the loop for one iteration
        Node<V> returnNode = tryPop();
        if (returnNode != null) {
            return returnNode.value;
        } else {
            return null;
        }
    }
}
