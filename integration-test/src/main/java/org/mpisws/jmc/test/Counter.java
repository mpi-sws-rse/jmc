package org.mpisws.jmc.test;

// A simple counter class that maintains an integer count.
// It provides methods to get and set the count, with JMC event logging for tracking changes.
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