package org.mpisws.jmc.programs.random.counter;

public class Counter {

    private int value;

    public Counter() {
        this.value = 0;
    }

    int getValue() {
        return this.value;
    }

    void increment() {
        this.value++;
    }

    void update(int delta) {
        this.value += delta;
    }
}
