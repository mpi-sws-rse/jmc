package org.mpisws.concurrent.programs.nondet.queue.msQueue;

import org.mpisws.concurrent.programs.nondet.queue.Queue;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class MSQueueII implements Queue {

    AtomicReference<Node> head, tail;

    public MSQueueII() {
        SymbolicInteger value = new SymbolicInteger("value", false);
        Node node = new Node(value);
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
    public void enq(SymbolicInteger x) throws JMCInterruptException {
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
    public SymbolicInteger deq() throws JMCInterruptException {
        Node head = this.head.get();
        Node node = head.next.get();

        if (node == null) {
            return null;
        }

        if (this.head.compareAndSet(head, node)) {
            return node.value;
        }

        return null;
    }
}
