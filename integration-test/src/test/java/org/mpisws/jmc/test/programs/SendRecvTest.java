package org.mpisws.jmc.test.programs;


import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.test.sendRecv.Buffer;
import org.mpisws.jmc.test.sendRecv.Receiver;
import org.mpisws.jmc.test.sendRecv.Sender;

public class SendRecvTest {

    private void sendRecvTest() {
        Buffer b = new Buffer(0);
        Sender sender = new Sender(b);
        Receiver receiver = new Receiver(b);

        receiver.start();
        sender.start();

        try {
            sender.join();
            receiver.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(2)
    public void runSendRecvTest() {
        sendRecvTest();
    }
}
