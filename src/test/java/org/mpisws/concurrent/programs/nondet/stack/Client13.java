package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.svStack.Consumer;
import org.mpisws.concurrent.programs.nondet.stack.svStack.Producer;
import org.mpisws.concurrent.programs.nondet.stack.svStack.SVStack;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client13 {

    public static void main(String[] args) {
        int SIZE = 5;
        SVStack stack = new SVStack(SIZE);
        ReentrantLock lock = new ReentrantLock();
        Producer producer = new Producer(stack, SIZE, lock);
        Consumer consumer = new Consumer(stack, SIZE, lock);
        producer.start();
        consumer.start();
        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
