package org.mpisws.concurrent.programs.sigma;

public class Shared {
    public int SIGMA;
    public int[] array;
    public int arrayIndex;// Shared index

    public Shared(int size) {
        this.SIGMA = size;
        this.array = new int[size];
        this.arrayIndex = -1;
    }
}
