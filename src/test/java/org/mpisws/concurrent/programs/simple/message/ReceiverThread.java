package org.mpisws.concurrent.programs.simple.message;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class ReceiverThread extends JMCThread {

    @Override
    public void run() {
        MessageServer.recv_msg();
    }

    @Override
    public void context() {}
}
