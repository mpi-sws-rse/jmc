package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.svStack.Consumer;
import org.mpisws.concurrent.programs.det.stack.svStack.Producer;
import org.mpisws.concurrent.programs.det.stack.svStack.SVStack;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client13 {

    public static void main(String[] args) {
        int SIZE = args.length;
        Integer[] arr = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }

        SVStack stack = new SVStack(SIZE);
        ReentrantLock lock = new ReentrantLock();
        Producer producer = new Producer(stack, SIZE, lock, arr);
        Consumer consumer = new Consumer(stack, SIZE, lock);
        producer.start();
        consumer.start();
        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }
}
