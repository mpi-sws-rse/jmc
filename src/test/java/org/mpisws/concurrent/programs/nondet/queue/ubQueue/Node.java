package org.mpisws.concurrent.programs.nondet.queue.ubQueue;

import org.mpisws.symbolic.SymbolicInteger;

public class Node {

    public SymbolicInteger value;
    public volatile Node next;

    public Node(SymbolicInteger value) {
        this.value = value;
        this.next = null;
    }
}
