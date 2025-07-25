package org.mpisws.jmc.test.sendRecv;

import org.mpisws.jmc.api.util.statements.JmcAssume;

public class Receiver extends Thread {

    private final Buffer buffer;

    public Receiver(Buffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void run() {
        JmcAssume.assume(buffer.getValue() == 1);
    }
}
