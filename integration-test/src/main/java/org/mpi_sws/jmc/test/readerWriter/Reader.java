package org.mpi_sws.jmc.test.readerWriter;

public class Reader extends Thread {

    Shared shared;

    public Reader(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        read();
    }

    private int read() {
        return shared.getValue();
    }
}
