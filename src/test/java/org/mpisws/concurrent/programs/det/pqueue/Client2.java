package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.concurrent.programs.det.pqueue.linear.LockBasedLinear;

public class Client2 {

    public static void main(String[] args) {

        int NUM_OPERATIONS = 5;
        int NUM_INSERTIONS = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_OPERATIONS / 2.0);


        int SIZE = args.length;
        int[] arr = new int[SIZE]; // Data domain is {0,1,2}
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }

        PQueue pqueue = new LockBasedLinear(SIZE);

        InsertionThread[] insertionThreads = new InsertionThread[SIZE];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            InsertionThread thread = new InsertionThread();
            thread.pqueue = pqueue;
            thread.item = i;
            thread.score = arr[i];
            insertionThreads[i] = thread;
        }

        DeletionThread[] deletionThreads = new DeletionThread[SIZE];
        for (int i = 0; i < NUM_DELETIONS; i++) {
            DeletionThread thread = new DeletionThread();
            thread.pqueue = pqueue;
            thread.item = i;
            deletionThreads[i] = thread;
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            insertionThreads[i].start();
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            deletionThreads[i].start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                insertionThreads[i].join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            try {
                deletionThreads[i].join();
            } catch (InterruptedException e) {

            }
        }
    }
}
