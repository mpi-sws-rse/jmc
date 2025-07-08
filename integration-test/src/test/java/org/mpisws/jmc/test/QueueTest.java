package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.det.queue.HWQueue;
import org.mpisws.jmc.test.det.queue.InsertionThread;
import org.mpisws.jmc.test.det.queue.Queue;

import java.util.ArrayList;

public class QueueTest {

    private void hwQueue_50_50_test(int NUM_OPERATIONS) {
        Queue q = new HWQueue(NUM_OPERATIONS);

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

            }
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "estimation", debug = false)
    public void runEstimationHWQueueTest() {
        hwQueue_50_50_test(6);
    }
}
