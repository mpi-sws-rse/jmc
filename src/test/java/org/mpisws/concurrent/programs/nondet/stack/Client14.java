package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.svStack.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client14 {

    public static void main(String[] args) {
        int SIZE = 6;
        SVStack stack = new SVStack(SIZE);
        ReentrantLock lock = new ReentrantLock();
        Shared shared = new Shared();
        Producer2 producer = new Producer2(stack, SIZE, lock, shared);
        Consumer2 consumer = new Consumer2(stack, SIZE, lock, shared);
        producer.start();
        consumer.start();
        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
