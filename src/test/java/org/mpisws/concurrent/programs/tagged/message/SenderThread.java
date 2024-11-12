package org.mpisws.concurrent.programs.tagged.message;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class SenderThread extends JMCThread {
    long receiver_tid;

    @Override
    public void run() {
        MessageServer.send_tagged_msg(receiver_tid, 100, "hello");
    }

    @Override
    public void context() {}
}
