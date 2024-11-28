package org.mpisws.concurrent.programs.nondet.queue.msQueue;

import org.mpisws.concurrent.programs.nondet.queue.Queue;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class MSQueue implements Queue {

    AtomicReference<Node> head, tail;

    public MSQueue() {
        SymbolicInteger value = new SymbolicInteger("value", false);
        Node node = new Node(value);
        head = new AtomicReference<Node>(node);
        tail = new AtomicReference<Node>(node);
    }

    public void enq(SymbolicInteger x) throws JMCInterruptException {
        Node node = new Node(x);
        while (true) {
            Node last = tail.get();
            Node next = last.next.get();
            if (last == tail.get()) {
                if (next == null) {
                    if (last.next.compareAndSet(next, node)) {
                        tail.compareAndSet(last, node);
                        return;
                    }
                } else {
                    tail.compareAndSet(last, next);
                }
            }
        }
    }

    public SymbolicInteger deq() throws JMCInterruptException {
        while (true) {
            Node first = head.get();
            Node last = tail.get();
            Node next = first.next.get();
            if (first == head.get()) {
                if (first == last) {
                    if (next == null) {
                        throw new JMCInterruptException();
                    }
                    tail.compareAndSet(last, next);
                } else {
                    SymbolicInteger value = next.value;
                    if (head.compareAndSet(first, next)) {
                        return value;
                    }
                }
            }
        }
    }
}
