package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.svStack.Consumer;
import org.mpisws.concurrent.programs.det.stack.svStack.Producer;
import org.mpisws.concurrent.programs.det.stack.svStack.SVStack;
import org.mpisws.util.concurrent.ReentrantLock;

public class Client13 {

    public static void main(String[] args) {
        int SIZE = 5;
        Integer[] arr = new Integer[SIZE]; // Data domain is {0,1,2}
        arr[0] = 1;
        arr[1] = 0;
        arr[2] = 2;
        arr[3] = 0;
        arr[4] = 1;
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
