package org.mpisws.concurrent.programs.det.queue.ubQueue;

public class Node {

    public int value;
    public volatile Node next;

    public Node(int value) {
        this.value = value;
        this.next = null;
    }
}
