package org.mpisws.concurrent.programs.message.counter;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class Counter extends JMCThread {

    final long INC = 100;

    long value = 0;

    @Override
    public void run() {
        Object val1 = MessageServer.recv_tagged_msg_block((tid, tag) -> tag == INC);
        Object val2 = MessageServer.recv_tagged_msg_block((tid, tag) -> tag == INC);
        if (val1 == null) {
            val1 = 0;
        }
        if (val2 == null) {
            val2 = 0;
        }
        value += (int) val1 + (int) val2;
        System.out.println("Counter value: " + value);
    }

    @Override
    public void context() {}
}
