package org.mpisws.util.concurrent;

public class TaggedMessage extends Message {
    public long tag;

    public TaggedMessage(long receiverTid, long senderTid, Object value, long tag) {
        super(receiverTid, senderTid, value);
        this.tag = tag;
    }
}
