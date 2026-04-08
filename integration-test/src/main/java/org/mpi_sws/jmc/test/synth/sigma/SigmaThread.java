package org.mpi_sws.jmc.test.synth.sigma;

public class SigmaThread extends Thread {

    public SigmaShared shared;

    public SigmaThread(SigmaShared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        shared.arrayIndex++;
        shared.array[shared.arrayIndex] = 1;
    }
}
