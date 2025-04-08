package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.svStack.*;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client14 {

    public static void main(String[] args) {
        int SIZE = args.length;
        Integer[] arr = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }

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
