package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.symbolic.SymbolicInteger;

public class Node {

    public SymbolicInteger value;
    public Node next;

    public Node(SymbolicInteger value) {
        this.value = value;
        this.next = null;
    }
}
