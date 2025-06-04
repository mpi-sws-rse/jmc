package org.mpisws.concurrent.programs.det.lists;

import org.mpisws.concurrent.programs.det.lists.list.Set;
import org.mpisws.concurrent.programs.det.lists.list.fine.FineList;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.SymbolicInteger;

import java.util.ArrayList;
import java.util.List;

public class Client10 {

    public static void main(String[] args) {

        int NUM_THREADS = 2;
        int NUM_INSERTIONS = (int) Math.ceil(NUM_THREADS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_THREADS / 2.0);

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        Set set = new FineList();


        List<SymbolicInteger> items = new ArrayList<>(NUM_INSERTIONS);
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            items.add(new SymbolicInteger(false, arr[i]));
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_INSERTIONS);
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            AbstractInteger item = items.get(i);
            threads.add(new InsertionThread(set, item));
        }

        List<DeletionThread> deleteThreads = new ArrayList<>(NUM_DELETIONS);
        for (int i = 0; i < NUM_DELETIONS; i++) {
            AbstractInteger item = items.get(i);
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
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            try {
                deleteThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }
}
