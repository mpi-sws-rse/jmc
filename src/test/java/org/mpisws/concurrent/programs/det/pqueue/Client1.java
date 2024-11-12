package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.concurrent.programs.det.pqueue.linear.LockBasedLinear;

public class Client1 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 5;
        PQueue pqueue = new LockBasedLinear(NUM_OPERATIONS);

        /*int[][] combinations = {
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 1},
                {0, 0, 0, 1, 1},
                {0, 0, 0, 1, 2},
                {0, 0, 1, 1, 2},
                {0, 0, 1, 2, 3},
                {0, 1, 2, 3, 4}
        };*/

        int[][] combinations = {
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 1, 0},
                {0, 0, 0, 1, 1, 0},
                {0, 0, 0, 1, 1, 1},
                {0, 0, 0, 1, 1, 2},
                {0, 0, 0, 1, 2, 3},
                {0, 0, 1, 2, 3, 4},
                {0, 0, 0, 1, 2, 0},
                {0, 0, 1, 1, 2, 2},
                {0, 0, 1, 1, 2, 3},
                {0, 1, 2, 3, 4, 5}
        };

        int round = 10;

        InsertionThread[] threads = new InsertionThread[NUM_OPERATIONS];
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            InsertionThread thread = new InsertionThread();
            thread.pqueue = pqueue;
            thread.item = i;
            thread.score = combinations[round][i];
            threads[i] = thread;
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads[i].start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {

            }
        }
    }
}
