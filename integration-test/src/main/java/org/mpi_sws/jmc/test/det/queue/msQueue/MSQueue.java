package org.mpi_sws.jmc.test.det.queue.msQueue;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;
import org.mpi_sws.jmc.test.det.queue.Queue;

import java.util.concurrent.atomic.AtomicReference;

public class MSQueue implements Queue {

    AtomicReference<Node> head, tail;

    public MSQueue() {
        Node node = new Node(0);
        head = new AtomicReference<Node>(node);
        tail = new AtomicReference<Node>(node);
    }

    @Override
    public void enq(int x) {
        Node node = new Node(x);
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
        JmcAssume.assume(false); // Used to model an infinite loop
    }

    @Override
    public int deq() {
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
        JmcAssume.assume(false); // Used to model an infinite loop
        return -1;
    }
}
