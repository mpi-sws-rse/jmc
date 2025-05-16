package org.mpisws.jmc.programs.twophasecommit;

import org.mpisws.jmc.runtime.RuntimeUtils;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;

import java.util.LinkedList;
import java.util.List;

public class Mailbox {
    private final List<Message> messages;
<<<<<<< Updated upstream
    private final JmcReentrantLock lock;

    public Mailbox() {
        this.messages = new LinkedList<>();
        RuntimeUtils.writeEvent(this, null, "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
        this.lock = new JmcReentrantLock();
=======
    private boolean closed;
    private final ReentrantLock lock;

    public Mailbox() {
        this.messages = new ArrayList<>();
        this.closed = false;
        this.lock = new ReentrantLock();
>>>>>>> Stashed changes
    }

    public void send(Message message) {
        lock.lock();
        try {
<<<<<<< Updated upstream
            RuntimeUtils.readEvent(this, "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
=======
            if (closed) {
                return;
            }
>>>>>>> Stashed changes
            messages.add(message);
            RuntimeUtils.writeEvent(this, message.toString(), "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        closed = true;
        lock.unlock();
    }

    public Message receive() {
        lock.lock();
        // TODO: should spin here and never return null unless the mailbox is closed
        try {
<<<<<<< Updated upstream
            RuntimeUtils.readEvent(this, "org/mpisws/jmc/programs/twophasecommit/Mailbox", "messages", "Ljava/util/List;");
            if (messages.isEmpty()) {
=======
            while (!closed && messages.isEmpty()) {
                // wait
            }
            if (closed && messages.isEmpty()) {
>>>>>>> Stashed changes
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
