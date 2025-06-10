package org.mpisws.concurrent.programs.violation;

public class Counter {

    private int count;

    public Counter() {
        this.count = 0;
    }

    public void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
