package org.mpisws.concurrent.programs.inconsistent.counter;

public class Counter {
    public int count;

    public Counter() {
        count = 0;
    }

    public int get() {
        return count;
    }

    public void set(int newValue) {
        count = newValue;
    }
}

