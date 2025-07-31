package org.mpi_sws.jmc.test.programs;


import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.test.sendRecv.Buffer;
import org.mpi_sws.jmc.test.sendRecv.Receiver;
import org.mpi_sws.jmc.test.sendRecv.Sender;

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
