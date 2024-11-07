package org.mpisws.concurrent.programs.sync.message;

public class SyncMessage {

    public static void main(String[] args) {
        ReceiverThread receiver = new ReceiverThread();
        SenderThread sender = new SenderThread();
        sender.receiver = receiver;
        receiver.start();
        sender.start();

        sender.joinThread();
        receiver.joinThread();

        System.out.println("Done");
    }
}
