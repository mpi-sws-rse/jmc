package org.mpi_sws.jmc.test.det.queue.msQueue;

import java.util.concurrent.atomic.AtomicReference;

public class Node {
    public int value;
    public AtomicReference<Node> next;

    public Node(int value) {
        this.value = value;
        this.next = new AtomicReference<Node>(null);
    }
}
