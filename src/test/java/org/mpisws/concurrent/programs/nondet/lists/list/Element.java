package org.mpisws.concurrent.programs.nondet.lists.list;

import org.mpisws.symbolic.AbstractInteger;

public class Element {

    public AbstractInteger key;

    public Element(AbstractInteger key) {
        this.key = key;
    }

    public AbstractInteger getHash() {
        return key;
    }
}
