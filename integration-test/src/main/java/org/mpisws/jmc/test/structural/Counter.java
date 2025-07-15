package org.mpisws.jmc.test.structural;

public class Counter {

    private int count;

    public Counter() {
        this.count = 0;
    }

    public int get() {
        return count;
    }

    public void set(int value) {
        count = value;
    }
}
