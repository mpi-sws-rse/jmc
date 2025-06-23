package org.mpisws.jmc.programs.twophasecommit;

public class Message {
    private final Type type;
    private final String content;
    private final int senderId;
    private final int receiverId;
    private final Mailbox responseMailbox;

    public Message(Type type, String content, int senderId, int receiverId) {
        this.content = content;
        this.type = type;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.responseMailbox = null;
    }

    public Message(
            Type type, String content, int senderId, int receiverId, Mailbox responseMailbox) {
        this.content = content;
        this.type = type;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.responseMailbox = responseMailbox;
    }

    public Mailbox getResponseMailbox() {
        return responseMailbox;
    }

    public Type getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public void write() {
        // Make call to runtime to indicate a write operation
    }

    public void read() {
        // Make call to runtime to indicate a read operation
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", content='" + content + '\'' +
                ", senderId=" + senderId +
                ", receiverId=" + receiverId +
                '}';
    }

    public enum Type {
        PREPARE,
        COMMIT,
        ABORT,
        ACKNOWLEDGE,
    }
}
