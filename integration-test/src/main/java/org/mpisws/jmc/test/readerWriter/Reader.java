package org.mpisws.jmc.test.readerWriter;

public class Reader extends Thread {

    Shared shared;

    public Reader(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        read();
    }

    private Shared read() {
        return shared;
    }
}
