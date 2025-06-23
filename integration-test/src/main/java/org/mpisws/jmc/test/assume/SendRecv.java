package org.mpisws.jmc.test.assume;

public class SendRecv {

    public static void main(String[] args) {

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
}
