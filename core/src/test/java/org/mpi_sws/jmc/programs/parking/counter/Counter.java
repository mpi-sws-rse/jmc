package org.mpi_sws.jmc.programs.parking.counter;

public class Counter {

    private int value = 0;

    public void inc() {
        value++;
    }

    public int getValue() {
        return value;
    }
}
