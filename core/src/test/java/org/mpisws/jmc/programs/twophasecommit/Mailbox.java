package org.mpisws.jmc.programs.twophasecommit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Mailbox {
    private final List<Message> messages;
    private final ReentrantLock lock;

    public Mailbox() {
        this.messages = new ArrayList<>();
        this.lock = new ReentrantLock();
    }

    public void send(Message message) {
        lock.lock();
        try {
            messages.add(message);
        } finally {
            lock.unlock();
        }
    }

    public Message receive() {
        lock.lock();
        try {
            if (messages.isEmpty()) {
                return null;
            }
            return messages.remove(0);
        } finally {
            lock.unlock();
        }
    }
}
