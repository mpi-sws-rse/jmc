package org.mpisws.util.concurrent;

public abstract class Message {
    public long threadId;
    public Object value;

    public Message(long threadId, Object value) {
        this.threadId = threadId;
        this.value = value;
    }
}
