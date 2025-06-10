package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.concurrent.programs.nondet.queue.svQueue.Consumer;
import org.mpisws.concurrent.programs.nondet.queue.svQueue.Producer;
import org.mpisws.concurrent.programs.nondet.queue.svQueue.SVQueue;
import org.mpisws.concurrent.programs.nondet.queue.svQueue.SharedState;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client11 {

    public static void main(String[] args) {
        int SIZE = 6;
        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer producer = new Producer(q, lock, SIZE, sharedState);
        Consumer consumer = new Consumer(q, lock, SIZE, sharedState);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
