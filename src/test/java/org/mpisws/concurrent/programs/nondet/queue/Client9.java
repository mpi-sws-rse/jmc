package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.concurrent.programs.nondet.queue.svQueue.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client9 {

    public static void main(String[] args) {
        int SIZE = 10;
        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer3 producer = new Producer3(q, lock, SIZE, sharedState);
        Consumer3 consumer = new Consumer3(q, lock, SIZE, sharedState);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
