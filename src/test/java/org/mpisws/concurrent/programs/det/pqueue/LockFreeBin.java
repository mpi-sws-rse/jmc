package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class LockFreeBin implements Bin {

    private final AtomicReference<Node> head;

    public LockFreeBin() {
        head = new AtomicReference<>(null);
    }

    @Override
    public void put(int item) throws JMCInterruptException {
        Node newNode = new Node(item);
        while (true) {
            Node oldHead = head.get();
            newNode.next = oldHead;
            if (head.compareAndSet(oldHead, newNode)) {
                return;
            }
        }
    }

    @Override
    public int get() throws JMCInterruptException {
        while (true) {
            Node oldHead = head.get();
            if (oldHead == null) {
                return -1;
            }
            Node newHead = oldHead.next;
            if (head.compareAndSet(oldHead, newHead)) {
                return oldHead.value;
            }
        }
    }
}
