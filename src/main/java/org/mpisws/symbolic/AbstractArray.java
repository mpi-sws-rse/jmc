package org.mpisws.symbolic;

public abstract class AbstractArray {

    public Type type;
    public int hash;

    /**
     * Makes a deep copy of the integer.
     *
     * @return a deep copy of the integer.
     */
    abstract AbstractArray deepCopy();

    public abstract AbstractArray read();

    public abstract void write(AbstractArray value);

    public void setHash(int hash) {
        this.hash = hash;
    }

    public int getHash() {
        return hash;
    }
}
