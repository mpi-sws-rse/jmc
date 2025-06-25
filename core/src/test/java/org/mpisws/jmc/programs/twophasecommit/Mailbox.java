package org.mpisws.jmc.programs.twophasecommit;

import org.mpisws.jmc.runtime.JmcRuntimeUtils;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;

import java.util.LinkedList;
import java.util.List;

public class Mailbox {
    private final List<Message> messages;
    private final JmcReentrantLock lock;

    public Mailbox() {
        this.messages = new LinkedList<>();
        JmcRuntimeUtils.writeEvent(
                null,
                "org/mpisws/jmc/programs/twophasecommit/Mailbox",
                "messages",
                "Ljava/util/List;",
                this);
        this.lock = new JmcReentrantLock();
    }

    public void send(Message message) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEvent(
                    "org/mpisws/jmc/programs/twophasecommit/Mailbox",
                    "messages",
                    "Ljava/util/List;",
                    this);
            messages.add(message);
            JmcRuntimeUtils.writeEvent(
                    this,
                    message.toString(),
                    "org/mpisws/jmc/programs/twophasecommit/Mailbox",
                    "messages",
                    "Ljava/util/List;");
        } finally {
            lock.unlock();
        }
    }

    public Message receive() {
        lock.lock();
        // TODO: should spin here and never return null unless the mailbox is closed
        try {
            JmcRuntimeUtils.readEvent(
                    "org/mpisws/jmc/programs/twophasecommit/Mailbox",
                    "messages",
                    "Ljava/util/List;",
                    this);
            if (messages.isEmpty()) {
                return null;
            }
            Message out = messages.remove(0);
            JmcRuntimeUtils.writeEvent(
                    out.toString(),
                    "org/mpisws/jmc/programs/twophasecommit/Mailbox",
                    "messages",
                    "Ljava/util/List;",
                    this);
            return out;
        } finally {
            lock.unlock();
        }
    }
}
