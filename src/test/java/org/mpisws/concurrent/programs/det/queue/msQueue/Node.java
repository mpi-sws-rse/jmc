package org.mpisws.concurrent.programs.det.queue.msQueue;

import org.mpisws.util.concurrent.AtomicReference;

public class Node {
    public int value;
    public AtomicReference<Node> next;

    public Node(int value) {
        this.value = value;
        this.next = new AtomicReference<Node>(null);
    }
}
