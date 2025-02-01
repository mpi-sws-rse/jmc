package org.mpisws.concurrent.programs.nondet.map.coarse;

import org.mpisws.symbolic.SymbolicInteger;

public class Node {

    public SymbolicInteger key;
    public int value;
    Node next;

    public Node(SymbolicInteger key, int value) {
        this.key = key;
        this.value = value;
        this.next = null;
    }
}
