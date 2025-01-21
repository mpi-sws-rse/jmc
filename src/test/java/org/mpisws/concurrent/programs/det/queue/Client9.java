package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.concurrent.programs.det.queue.svQueue.Consumer3;
import org.mpisws.concurrent.programs.det.queue.svQueue.Producer3;
import org.mpisws.concurrent.programs.det.queue.svQueue.SVQueue;
import org.mpisws.concurrent.programs.det.queue.svQueue.SharedState;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client9 {

    public static void main(String[] args) {
        int SIZE = args.length;
        int[] arr = new int[SIZE]; // Data domain is {0,1,2}
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }

        SVQueue q = new SVQueue(SIZE);
        SharedState sharedState = new SharedState(SIZE);
        ReentrantLock lock = new ReentrantLock();

        Producer3 producer = new Producer3(q, lock, SIZE, sharedState, arr);
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
