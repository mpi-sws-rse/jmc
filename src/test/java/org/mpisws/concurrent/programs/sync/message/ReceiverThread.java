package org.mpisws.concurrent.programs.sync.message;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class ReceiverThread extends JMCThread {

    @Override
    public void context() {
        Object message = MessageServer.recv_tagged_msg_block((sender_tid, tag) -> tag == 10);
        System.out.println("Received message: " + message);
    }
}
