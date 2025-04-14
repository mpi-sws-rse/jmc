package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.concurrent.programs.nondet.queue.svQueue.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client16 {

    public static void main(String[] args) {
        int SIZE = 16;
        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer7 producer = new Producer7(q, lock, SIZE, sharedState);
        Consumer7 consumer = new Consumer7(q, lock, sharedState, SIZE);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
