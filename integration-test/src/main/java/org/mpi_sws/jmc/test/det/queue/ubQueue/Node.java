package org.mpi_sws.jmc.test.det.queue.ubQueue;

public class Node {

    public int value;
    public volatile Node next;

    public Node(int value) {
        this.value = value;
        this.next = null;
    }
}
