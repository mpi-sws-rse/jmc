package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

public abstract class JMCThread extends Thread {

    private final List<Message> queue = new ArrayList<>();

    public JMCThread() {
        RuntimeEnvironment.addThread(this);
    }

    public synchronized void pushMessage(Message message) {
        this.queue.add(message);
    }

    @Override
    public void start() {
        RuntimeEnvironment.threadStart(this, currentThread());
    }

    public void joinThread() {
        RuntimeEnvironment.threadJoin(this, currentThread());
        RuntimeEnvironment.waitRequest(currentThread());
    }

}
