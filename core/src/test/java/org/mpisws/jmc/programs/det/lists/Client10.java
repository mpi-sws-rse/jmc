package org.mpisws.jmc.programs.det.lists;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.coarse.CoarseList;
import org.mpisws.jmc.programs.det.lists.list.fine.FineList;
import org.mpisws.jmc.util.concurrent.JmcThread;

import java.util.ArrayList;
import java.util.List;

public class Client10 {

    public static void main(String[] args) {
        int NUM_THREADS = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        int NUM_INSERTIONS = (int) Math.ceil(NUM_THREADS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_THREADS / 2.0);

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        Set set = new FineList();

        List<JmcThread> insertionThreads = new ArrayList<>(NUM_INSERTIONS);
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            int item = arr[i];
            InsertionThread ithread = new InsertionThread(set, item);
            insertionThreads.add(ithread);
        }

        List<JmcThread> deleteThreads = new ArrayList<>(NUM_DELETIONS);
        for (int i = 0; i < NUM_DELETIONS; i++) {
            int item = arr[i];
            DeletionThread dthread = new DeletionThread(set, item);
            deleteThreads.add(dthread);
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            insertionThreads.get(i).start();
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            deleteThreads.get(i).start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                insertionThreads.get(i).join1();
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
