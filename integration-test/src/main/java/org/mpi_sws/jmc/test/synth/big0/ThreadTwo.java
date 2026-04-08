package org.mpi_sws.jmc.test.synth.big0;

public class ThreadTwo extends Thread {

    Data data;

    public ThreadTwo(Data data) {
        this.data = data;
    }

    @Override
    public void run() {
        data.getX();
        data.getY();
        data.getZ();
    }
}
