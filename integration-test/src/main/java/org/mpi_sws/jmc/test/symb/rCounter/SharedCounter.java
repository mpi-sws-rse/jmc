package org.mpi_sws.jmc.test.symb.rCounter;

public class SharedCounter {

    private int value;

    public SharedCounter() {
        this.value = 0;
    }

    int getValue() {
        return this.value;
    }

    void increment() {
        this.value++;
    }
}
