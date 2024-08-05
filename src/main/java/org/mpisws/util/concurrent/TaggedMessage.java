package org.mpisws.util.concurrent;

public class TaggedMessage extends Message {
    public long tag;

    public TaggedMessage(long threadId, Object value, long tag) {
        super(threadId, value);
        this.tag = tag;
    }
}
