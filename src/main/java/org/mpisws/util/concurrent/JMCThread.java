package org.mpisws.util.concurrent;

import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import programStructure.Message;
import programStructure.TaggedMessage;

public abstract class JMCThread extends Thread {

    private final List<Message> queue = new ArrayList<>();

    public int nextMessageIndex;

    public JMCThread() {
        RuntimeEnvironment.addThread(this);
    }

    public synchronized void pushMessage(Message message) {
        this.queue.add(message);
    }

    public void joinThread() {
        RuntimeEnvironment.threadJoin(this, currentThread());
        RuntimeEnvironment.waitRequest(currentThread());
    }

    public Message findMessage() {
        if (nextMessageIndex >= 0) {
            return this.queue.remove(nextMessageIndex);
        } else {
            return null;
        }
    }

    public void findNextMessageIndex(Message message) {
        nextMessageIndex = queue.indexOf(message);
    }

    public List<Message> computePredicateMessage(BiFunction<Long, Long, Boolean> function) {
        List<Message> collectedMessages = new ArrayList<>();
        for (Message message : queue) {
            if (message instanceof TaggedMessage taggedMessage) {
                boolean result = function.apply(taggedMessage.getReceiverThreadId(), taggedMessage.getTag());
                if (result) {
                    collectedMessages.add(taggedMessage);
                }
            }
        }
        return collectedMessages;
    }

    public void noMessageExists() {
        nextMessageIndex = -1;
    }

    public Message findRandomMessage(Random random) {
        if (queue.isEmpty()) {
            return null;
        } else {
            int randomIndex = random.nextInt(queue.size());
            nextMessageIndex = randomIndex;
            return queue.get(randomIndex);
        }
    }

    @Override
    public void run() {
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        this.context();
        try {
            RuntimeEnvironment.finishThreadRequest(Thread.currentThread());
        } catch (HaltExecutionException e) {
            throw new RuntimeException(e);
            // TODO(): CHECK try catch block
        }
    }

    public void context() {
        System.out.println("[JMC Message] : You need to override context method for each JMCThread");
    }

}