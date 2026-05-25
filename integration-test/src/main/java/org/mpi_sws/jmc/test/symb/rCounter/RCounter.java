package org.mpi_sws.jmc.test.symb.rCounter;

public class RCounter {

    private int value;

    public RCounter() {
        this.value = 0;
    }

    int getValue() {
        return this.value;
    }

    void increment() {
        this.value++;
    }
}
