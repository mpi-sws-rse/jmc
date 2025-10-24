package org.mpi_sws.jmc.test.det.queue.msQueue;

import org.mpi_sws.jmc.test.det.queue.Queue;

import java.util.concurrent.atomic.AtomicReference;

public class MSQueue implements Queue {

    AtomicReference<Node> head, tail;

    public MSQueue() {
        Node node = new Node(0);
        head = new AtomicReference<Node>(node);
        tail = new AtomicReference<Node>(node);
    }

    private Node findTail() {
        Node node = tail.get();
        Node next = node.next.get();

        if (next == null) {
            return node;
        }

        tail.set(next);
        return null;
    }

    @Override
    public void enq(int x) {
        Node node = new Node(x);

        node.next.set(null);

        Node tail;
        do {
            tail = findTail();
        } while (tail == null);

        if (tail.next.compareAndSet(null, node)) {
            tail.next.set(node);
        }
    }

    @Override
    public int deq() {
        Node head = this.head.get();
        Node node = head.next.get();

        if (node == null) {
            return 0;
        }

        if (this.head.compareAndSet(head, node)) {
            return node.value;
        }

        return 0;
    }
}
