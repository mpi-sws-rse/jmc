package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.test.features.channels.Channel;

public class SenderThread extends Thread {
    private final Channel channel;
    private final int messagesToSend;

    public SenderThread(Channel channel, int messagesToSend) {
        this.channel = channel;
        this.messagesToSend = messagesToSend;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < messagesToSend; i++) {
                channel.send("Message " + i);
            }
        } catch (InterruptedException e) {
            System.out.println("Sender interrupted" + e);
        }
    }
}
