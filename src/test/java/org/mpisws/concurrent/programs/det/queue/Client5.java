package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.concurrent.programs.det.queue.ubQueue.UnboundedQueue;

import java.util.ArrayList;

public class Client5 {

    public static void main(String[] args) {
        Queue q = new UnboundedQueue();
        int NUM_OPERATIONS = 1;

        ArrayList items = new ArrayList(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i + 1);
        }

        ArrayList threads = new ArrayList(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            int item = (int) items.get(i);
            InsertionThread thread = new InsertionThread(q, item);
            threads.add(thread);
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            ((InsertionThread) threads.get(i)).start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                ((InsertionThread) threads.get(i)).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
