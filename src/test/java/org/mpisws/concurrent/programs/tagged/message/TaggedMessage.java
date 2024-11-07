package org.mpisws.concurrent.programs.tagged.message;

public class TaggedMessage {

    public static void main(String[] args) {
        SenderThread senderThread = new SenderThread();
        ReceiverThread receiverThread = new ReceiverThread();

        senderThread.receiver_tid = receiverThread.getId();
        receiverThread.knownThread = senderThread.getId();

        senderThread.start();
        receiverThread.start();

        senderThread.joinThread();
        receiverThread.joinThread();

        System.out.println("Tagged Message Completed");
    }
}
