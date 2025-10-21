package org.mpi_sws.jmc.test.readerWriter;

public class Shared {

    private int value = 0;

    public Shared(int v) {
        this.value = v;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void incValue() {
        this.value++;
    }
}
