package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.concurrent.programs.nondet.queue.ubQueue.UnboundedQueue;
import org.mpisws.symbolic.SymbolicInteger;

public class Client5 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 3;
        Queue q = new UnboundedQueue();

        SymbolicInteger[] items = new SymbolicInteger[NUM_OPERATIONS];
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items[i] = new SymbolicInteger("item" + i, false);
        }

        InsertionThread[] threads = new InsertionThread[NUM_OPERATIONS];
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            SymbolicInteger item = items[i];
            InsertionThread thread = new InsertionThread(q, item);
            threads[i] = thread;
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads[i].start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
