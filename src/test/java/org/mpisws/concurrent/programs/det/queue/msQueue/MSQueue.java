package org.mpisws.concurrent.programs.det.queue.msQueue;

import org.mpisws.concurrent.programs.det.queue.Queue;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class MSQueue implements Queue {

    AtomicReference<Node> head, tail;

    public MSQueue() {
        Node node = new Node(-1);
        head = new AtomicReference<Node>(node);
        tail = new AtomicReference<Node>(node);
    }

    public void enq(int x) throws JMCInterruptException {
        Node node = new Node(x);
        /*while (true) {
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
        }*/
        // The unrolled version of the above loop for one iteration
        Node last = tail.get();
        Node next = last.next.get();
        if (last == tail.get()) {
            if (next == null) {
                if (last.next.compareAndSet(next, node)) {
                    tail.compareAndSet(last, node);
                }
            } else {
                tail.compareAndSet(last, next);
            }
        }
    }

    public int deq() throws JMCInterruptException {
        /*while (true) {
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
                    int value = next.value;
                    if (head.compareAndSet(first, next)) {
                        return value;
                    }
                }
            }
        }*/
        // The unrolled version of the above loop for one iteration
        Node first = head.get();
        Node last = tail.get();
        Node next = first.next.get();
        if (first == head.get()) {
            if (first == last) {
                if (next == null) {
                    return -1;
                }
                tail.compareAndSet(last, next);
            } else {
                int value = next.value;
                if (head.compareAndSet(first, next)) {
                    return value;
                }
            }
        }
        return -1;
    }
}
