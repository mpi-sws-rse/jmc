package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.concurrent.programs.det.queue.lbQueue.LBQueue;

import java.util.ArrayList;

public class Client3 {

    public static void main(String[] args) {
        int SIZE = args.length;
        int[] items = new int[SIZE]; // Data domain is {1,2,3}
        for (int i = 0; i < SIZE; i++) {
            items[i] = Integer.parseInt(args[i]);
        }

        Queue q = new LBQueue(SIZE);


        ArrayList threads = new ArrayList(SIZE);
        for (int i = 0; i < SIZE; i++) {
            int item = items[i];
            InsertionThread thread = new InsertionThread(q, item);
            threads.add(thread);
        }

        for (int i = 0; i < SIZE; i++) {
            ((InsertionThread) threads.get(i)).start();
        }

        for (int i = 0; i < SIZE; i++) {
            try {
                ((InsertionThread) threads.get(i)).join();
            } catch (InterruptedException e) {

            }
        }
    }
}
