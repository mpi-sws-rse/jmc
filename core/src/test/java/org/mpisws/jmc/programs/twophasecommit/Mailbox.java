package org.mpisws.jmc.programs.twophasecommit;

import org.mpisws.jmc.runtime.RuntimeUtils;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;

import java.util.LinkedList;
import java.util.List;

public class Mailbox {
    private final List<Message> messages;
    private final JmcReentrantLock lock;

    public Mailbox() {
        this.messages = new LinkedList<>();
        RuntimeUtils.writeEvent(this, null, "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
        this.lock = new JmcReentrantLock();
    }

    public void send(Message message) {
        lock.lock();
        try {
            RuntimeUtils.readEvent(this, "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
            messages.add(message);
            RuntimeUtils.writeEvent(this, message.toString(), "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
        } finally {
            lock.unlock();
        }
    }

    public Message receive() {
        lock.lock();
        try {
            RuntimeUtils.readEvent(this, "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
            if (messages.isEmpty()) {
                return null;
            }
            Message out = messages.remove(0);
            RuntimeUtils.writeEvent(this, out.toString(), "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
            return out;
        } finally {
            lock.unlock();
        }
    }
}
