package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.concurrent.programs.nondet.queue.hwQueue.HWQueue;
import org.mpisws.symbolic.SymbolicInteger;

public class Client2 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 2;
        int NUM_INSERTIONS = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_OPERATIONS / 2.0);
        Queue q = new HWQueue(NUM_INSERTIONS);

        SymbolicInteger[] items = new SymbolicInteger[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            items[i] = new SymbolicInteger("k" + i, false);
        }

        InsertionThread[] insertionThreads = new InsertionThread[NUM_OPERATIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            SymbolicInteger item = items[i];
            InsertionThread thread = new InsertionThread(q, item);
            insertionThreads[i] = thread;
        }

        DeletionThread[] deletionThreads = new DeletionThread[NUM_DELETIONS];
        for (int i = 0; i < NUM_DELETIONS; i++) {
            DeletionThread thread = new DeletionThread(q);
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
