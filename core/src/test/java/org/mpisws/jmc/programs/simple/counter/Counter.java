package org.mpisws.jmc.programs.simple.counter;

public class Counter {
    private int count = 0;

    public int getValue() {
        return count;
    }

    public void increment() {
        count++;
    }
}
