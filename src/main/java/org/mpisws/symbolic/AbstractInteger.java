package org.mpisws.symbolic;

import java.io.Serializable;

public abstract class AbstractInteger implements Serializable {

    private int value;

    abstract AbstractInteger deepCopy();

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
