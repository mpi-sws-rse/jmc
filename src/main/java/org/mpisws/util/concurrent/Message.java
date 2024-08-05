package org.mpisws.util.concurrent;

public abstract class Message {

    /**
     * Thread Id
     */
    public long threadId;
    public Object value;

    public Message(long threadId, Object value) {
        this.threadId = threadId;
        this.value = value;
    }
}
