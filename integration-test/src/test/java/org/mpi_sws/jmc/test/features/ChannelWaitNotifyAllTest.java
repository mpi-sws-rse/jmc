package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.test.features.channels.ChannelWaitNotifyAll;

public class ChannelWaitNotifyAllTest {

    private void testProgramWaitNotifyAll() {
        ChannelWaitNotifyAll channel = new ChannelWaitNotifyAll(1);
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
    // TODO :: Check this test
    public void testChannel() {
        testProgramWaitNotifyAll();
    }
}
