package org.mpisws.concurrent.programs.simple.message;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class SenderThread extends JMCThread {
    long receiver_tid;

    @Override
    public void run() {
        MessageServer.send_msg(receiver_tid, "hello");
    }
}
