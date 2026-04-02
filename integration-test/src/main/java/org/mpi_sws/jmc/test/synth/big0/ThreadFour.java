package org.mpi_sws.jmc.test.synth.big0;

public class ThreadFour extends Thread {

    Data data;

    public ThreadFour(Data data) {
        this.data = data;
    }

    @Override
    public void run() {
        data.setZ(1);
        data.setW(1);
        data.setZ(2);
        data.setW(2);
        data.setZ(3);
        data.setW(3);
    }
}
