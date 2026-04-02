package org.mpi_sws.jmc.test.synth.singletone;

public class SingletoneThread1 extends Thread {

    SingletoneShared shared;

    public SingletoneThread1(SingletoneShared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        shared.c = '0';
    }
}
