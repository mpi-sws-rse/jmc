package org.mpisws.jmc.test.programs;

/** The Counter class is used to store the counter. */
public class CounterITest {
    private int counter;

    public CounterITest() {
        this.counter = 0;
    }

    public void increment() {
        this.counter = this.counter + 1;
    }

    public int getCounter() {
        return counter;
    }
}
