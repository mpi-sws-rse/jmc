package org.mpisws.jmc.agent.programs.det.list;

import org.mpisws.jmc.agent.programs.det.list.coarse.CoarseList;
import org.mpisws.jmc.util.concurrent.JmcThread;

import java.util.ArrayList;
import java.util.List;

public class Client9 {

    public static void main(String[] args) {

        int NUM_THREADS = 5;
        int NUM_INSERTIONS = (int) Math.ceil(NUM_THREADS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_THREADS / 2.0);

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        Set set = new CoarseList();

        List<JmcThread> threads = new ArrayList<>(NUM_INSERTIONS);
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            int item = arr[i];
            threads.add(new InsertionThread(set, item));
        }

        List<JmcThread> deleteThreads = new ArrayList<>(NUM_DELETIONS);
        for (int i = 0; i < NUM_DELETIONS; i++) {
            int item = arr[i];
            deleteThreads.add(new DeletionThread(set, item));
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            deleteThreads.get(i).start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads.get(i).join1();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            try {
                deleteThreads.get(i).join1();
            } catch (InterruptedException e) {

            }
        }
    }
}
