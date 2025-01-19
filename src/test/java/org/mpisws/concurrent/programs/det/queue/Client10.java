package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.concurrent.programs.det.queue.svQueue.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client10 {

    public static void main(String[] args) {
        int SIZE = 4;
        int[] arr = new int[SIZE]; // Data domain is {0,1,2}
        arr[0] = 0;
        arr[1] = 1;
        arr[2] = 2;
        arr[3] = 2;

        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer2 producer = new Producer2(q, lock, SIZE, sharedState, arr);
        Consumer2 consumer = new Consumer2(q, lock, SIZE, sharedState);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
