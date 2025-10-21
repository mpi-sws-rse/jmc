package org.mpi_sws.jmc.test.synth.big0;

public class ThreadOne extends Thread {

    Data data;

    public ThreadOne(Data data) {
        this.data = data;
    }

    @Override
    public void run() {
        data.getX();
        data.getY();
        data.getZ();
        data.getW();
        data.getX();
        data.getY();
        data.getZ();
    }
}
