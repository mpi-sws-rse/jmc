package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.concurrent.programs.nondet.queue.svQueue.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client14 {

    public static void main(String[] args) {
        int SIZE = 6;
        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer4 producer = new Producer4(q, lock, SIZE, sharedState);
        Consumer4 consumer = new Consumer4(q, lock, SIZE, sharedState);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
