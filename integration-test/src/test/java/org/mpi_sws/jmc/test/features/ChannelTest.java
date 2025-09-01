package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

public class ChannelTest {

    private void testProgram() {
        Channel channel = new Channel(1);
        SenderThread sender = new SenderThread(channel, 1);
        ReceiverThread receiver = new ReceiverThread(channel, 1);

        receiver.start();
        sender.start();

        try {
            sender.join();
            receiver.join();
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted" + e);
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testChannel() {
        testProgram();
    }
}
