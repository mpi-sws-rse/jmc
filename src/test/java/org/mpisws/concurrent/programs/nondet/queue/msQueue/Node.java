package org.mpisws.concurrent.programs.nondet.queue.msQueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.AtomicReference;

public class Node {

    public SymbolicInteger value;
    public AtomicReference<Node> next;

    public Node(SymbolicInteger value) {
        this.value = value;
        this.next = new AtomicReference<Node>(null);
    }
}
