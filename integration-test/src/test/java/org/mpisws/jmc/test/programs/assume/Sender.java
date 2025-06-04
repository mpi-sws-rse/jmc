package org.mpisws.jmc.test.programs.assume;

public class Sender extends Thread {

    private final Buffer buffer;

    public Sender(Buffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void run() {
        buffer.setValue(1);
    }
}
