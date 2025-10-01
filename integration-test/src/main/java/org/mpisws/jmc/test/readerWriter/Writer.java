package org.mpisws.jmc.test.readerWriter;

public class Writer extends Thread {

    Shared shared;

    public Writer(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        write();
    }

    public void write() {
        shared.incValue();
        shared.incValue();
        shared.incValue();
    }
}
