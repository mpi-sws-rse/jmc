package org.mpisws.jmc.test.det.counter;

import org.mpisws.jmc.test.det.counter.fine.DecThread;
import org.mpisws.jmc.test.det.counter.fine.FCounter;
import org.mpisws.jmc.test.det.counter.fine.IncThread;

public class Client2 {

    public static void main(String[] args) {
        int SIZE = args.length;

        FCounter counter = new FCounter();
        int NUM_INSERTIONS = (int) Math.ceil(SIZE / 2.0);
        int NUM_DELETIONS = (int) Math.floor(SIZE / 2.0);

        int i;
        IncThread[] threads = new IncThread[NUM_INSERTIONS];
        for (i = 0; i < NUM_INSERTIONS; i++) {
            int arg = Integer.parseInt(args[i]);
            threads[i] = new IncThread(counter, arg);
        }

        DecThread[] threads2 = new DecThread[NUM_DELETIONS];
        for (i = NUM_INSERTIONS; i < SIZE; i++) {
            int arg = Integer.parseInt(args[i]);
            threads2[i - NUM_INSERTIONS] = new DecThread(counter, arg);
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            threads[i].start();
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            threads2[i].start();
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
            }
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            try {
                threads2[i].join();
            } catch (InterruptedException e) {
            }
        }
    }
}
