package org.mpi_sws.jmc.test.det.queue.abaMsQueue;

import java.util.concurrent.atomic.AtomicLong;

public class Node {
    public int value;
    public AtomicLong next; // Tagged pointer

    public Node(int value) {
        this.value = value;
        this.next = new AtomicLong(0);
    }
}
