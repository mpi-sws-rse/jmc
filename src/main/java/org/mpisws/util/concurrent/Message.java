package org.mpisws.util.concurrent;

public abstract class Message {

    /** Thread Id */
    public long receiverThreadId;

    public long senderThreadId;
    public Object value;

    public Message(long receiverThreadId, long senderThreadId, Object value) {
        this.receiverThreadId = receiverThreadId;
        this.senderThreadId = senderThreadId;
        this.value = value;
    }

    @Override
    public String toString() {
        return "receiverId = "
                + receiverThreadId
                + " senderId = "
                + senderThreadId
                + " value = "
                + value;
    }
}
