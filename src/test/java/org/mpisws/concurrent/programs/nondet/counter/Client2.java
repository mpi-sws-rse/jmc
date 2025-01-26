package org.mpisws.concurrent.programs.nondet.counter;

import org.mpisws.concurrent.programs.nondet.counter.fine.DecThread;
import org.mpisws.concurrent.programs.nondet.counter.fine.FCounter;
import org.mpisws.concurrent.programs.nondet.counter.fine.IncThread;

public class Client2 {

    public static void main(String[] args) {
        FCounter counter = new FCounter();
        int SIZE = 7;
        int NUM_INSERTIONS = (int) Math.ceil(SIZE / 2.0);
        int NUM_DELETIONS = (int) Math.floor(SIZE / 2.0);

        IncThread[] threads = new IncThread[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            threads[i] = new IncThread(counter, "Inc-" + i);
        }

        DecThread[] threads2 = new DecThread[NUM_DELETIONS];
        for (int i = 0; i < NUM_DELETIONS; i++) {
            threads2[i] = new DecThread(counter, "Dec-" + i);
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            threads[i].start();
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            threads2[i].start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
            }
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            try {
                threads2[i].join();
            } catch (InterruptedException e) {
            }
        }
    }
}
