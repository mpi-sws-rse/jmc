package org.mpisws.jmc.test.readerWriter;

public class Incrementor extends Thread {

    Shared shared;

    public Incrementor(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        write();
    }

    public void write() {
        shared.incValue();
    }
}
