package org.mpi_sws.jmc.test.synth.sigma;

public class SigmaShared {
    public int SIGMA;
    public int[] array;
    public int arrayIndex;// Shared index

    public SigmaShared(int size) {
        this.SIGMA = size;
        this.array = new int[size];
        this.arrayIndex = -1;
    }
}
