package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.test.features.channels.Channel;

public class ReceiverThread extends Thread {
    private final Channel channel;
    private final int messagesToReceive;

    public ReceiverThread(Channel channel, int messagesToReceive) {
        this.channel = channel;
        this.messagesToReceive = messagesToReceive;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < messagesToReceive; i++) {
                Object message = channel.receive();
                System.out.println("Received: " + message);
            }
        } catch (InterruptedException e) {
            System.out.println("Receiver interrupted" + e);
        }
    }
}
