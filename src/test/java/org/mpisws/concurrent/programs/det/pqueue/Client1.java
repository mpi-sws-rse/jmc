package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.concurrent.programs.det.pqueue.linear.LockBasedLinear;

public class Client1 {

    public static void main(String[] args) {
        int SIZE = args.length;
        int[] arr = new int[SIZE]; // Data domain is {0,1,2}
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }

        PQueue pqueue = new LockBasedLinear(SIZE);

        InsertionThread[] threads = new InsertionThread[SIZE];
        for (int i = 0; i < SIZE; i++) {
            InsertionThread thread = new InsertionThread();
            thread.pqueue = pqueue;
            thread.item = i;
            thread.score = arr[i];
            threads[i] = thread;
        }

        for (int i = 0; i < SIZE; i++) {
            threads[i].start();
        }

        for (int i = 0; i < SIZE; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {

            }
        }
    }
}
