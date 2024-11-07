package org.mpisws.concurrent.programs.parking.counter;

public class Counter {

    private int value = 0;

    public void inc() {
        value++;
    }

    public int getValue() {
        return value;
    }
}
