package org.mpisws.util.concurrent;

import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import programStructure.Message;
import programStructure.ReceiveEvent;
import programStructure.TaggedMessage;

public abstract class JMCThread extends Thread {

    private final List<Message> queue = new ArrayList<>();

    private int nextMessageIndex = -1;

    public JMCThread() {
        RuntimeEnvironment.addThread(this);
    }

    public final synchronized void pushMessage(Message message) {
        this.queue.add(message);
    }

    public final void joinThread() {
        RuntimeEnvironment.threadJoin(this, currentThread());
        RuntimeEnvironment.waitRequest(currentThread());
    }

    public final Message findMessage() {
        if (nextMessageIndex >= 0) {
            Message message = this.queue.get(nextMessageIndex);
            nextMessageIndex = -1;
            return message;
        } else {
            return null;
        }
    }

    public final void findNextMessageIndex(Message message) {
        if (queue.contains(message)) {
            nextMessageIndex = queue.indexOf(message);
        } else {
            nextMessageIndex = -1;
        }
    }

    public final List<Message> computePredicateMessage(BiFunction<Long, Long, Boolean> function) {
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

    public final boolean isPerdicateSatisfiable(BiFunction<Long, Long, Boolean> function) {
        for (Message message : queue) {
            if (message instanceof TaggedMessage taggedMessage) {
                boolean result = function.apply(taggedMessage.getReceiverThreadId(), taggedMessage.getTag());
                if (result) {
                    return true;
                }
            }
        }
        return false;
    }

    public final void noMessageExists() {
        nextMessageIndex = -1;
    }

    public final Message findRandomMessage(Random random) {
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

    public final int getNextMessageIndex() {
        return nextMessageIndex;
    }

    public final boolean isMessageQueueEmpty() {
        return queue.isEmpty();
    }
}