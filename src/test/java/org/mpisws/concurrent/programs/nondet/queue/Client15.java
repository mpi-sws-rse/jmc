package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.concurrent.programs.nondet.queue.svQueue.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client15 {

    public static void main(String[] args) {
        int SIZE = 3;
        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer5 producer = new Producer5(q, lock, sharedState, SIZE);
        Consumer5 consumer = new Consumer5(q, lock, sharedState, SIZE);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
