package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.svStack.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client14 {

    public static void main(String[] args) {
        int SIZE = 5;
        Integer[] arr = new Integer[SIZE]; // Data domain is {0,1,2}
        arr[0] = 0;
        arr[1] = 1;
        arr[2] = 2;
        arr[3] = 0;
        arr[4] = 1;

        SVStack stack = new SVStack(SIZE);
        ReentrantLock lock = new ReentrantLock();
        Shared shared = new Shared();
        Producer2 producer = new Producer2(stack, SIZE, lock, shared, arr);
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
