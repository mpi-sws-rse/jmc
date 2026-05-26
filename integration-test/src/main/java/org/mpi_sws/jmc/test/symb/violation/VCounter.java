package org.mpi_sws.jmc.test.symb.violation;

public class VCounter {

    private int count;

    public VCounter() {
        this.count = 0;
    }

    public void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
