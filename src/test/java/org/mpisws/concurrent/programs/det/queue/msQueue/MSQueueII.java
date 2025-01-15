package org.mpisws.concurrent.programs.det.queue.msQueue;

import org.mpisws.concurrent.programs.det.queue.Queue;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class MSQueueII implements Queue {

    AtomicReference<Node> head, tail;

    public MSQueueII() {
        Node node = new Node(0);
        head = new AtomicReference<Node>(node);
        tail = new AtomicReference<Node>(node);
    }

    private Node findTail() throws JMCInterruptException {
        Node node = tail.get();
        Node next = node.next.get();

        if (next == null) {
            return node;
        }

        tail.set(next);
        return null;
    }

    /**
     * @param x
     * @throws JMCInterruptException
     */
    @Override
    public void enq(int x) throws JMCInterruptException {
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

    /**
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public int deq() throws JMCInterruptException {
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
