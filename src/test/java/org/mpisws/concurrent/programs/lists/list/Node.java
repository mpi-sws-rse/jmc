package org.mpisws.concurrent.programs.lists.list;

import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ConcreteInteger;

public class Node {

    public AbstractInteger item;

    public int key;

    public Node next;

    public Node(AbstractInteger i) {
        item = i;
        key = i.hashCode();
    }

    public Node(int i) {
        item = new ConcreteInteger(i);
        key = i;
    }
}
