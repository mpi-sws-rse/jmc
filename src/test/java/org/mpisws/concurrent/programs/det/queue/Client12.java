package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.concurrent.programs.det.queue.msQueue.MSQueue;

import java.util.ArrayList;

public class Client12 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 1;
        Queue q = new MSQueue();

        ArrayList items = new ArrayList(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i);
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
                //e.printStackTrace();
            }
        }
    }
}
