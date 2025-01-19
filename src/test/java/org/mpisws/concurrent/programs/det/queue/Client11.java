package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.concurrent.programs.det.queue.svQueue.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client11 {

    public static void main(String[] args) {
        int SIZE = 4;
        int[] arr = new int[SIZE]; // Data domain is {0,1,2}
        arr[0] = 2;
        arr[1] = 0;
        arr[2] = 2;
        arr[3] = 0;


        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer producer = new Producer(q, lock, SIZE, sharedState, arr);
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
