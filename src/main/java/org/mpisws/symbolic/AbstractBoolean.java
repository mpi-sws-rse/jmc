package org.mpisws.symbolic;

import java.io.Serializable;

public abstract class AbstractBoolean implements Serializable {

    private boolean value;

    abstract AbstractBoolean deepCopy();

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }
}
