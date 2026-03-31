package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.test.features.channels.Channel;
import org.mpi_sws.jmc.test.features.channels.ChannelWaitNotify;

public class ChannelWaitNotifyTest {

    private void testProgram() {
        Channel channel = new ChannelWaitNotify();
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
