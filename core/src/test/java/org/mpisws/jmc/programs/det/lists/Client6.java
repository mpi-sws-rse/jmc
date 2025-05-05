package org.mpisws.jmc.programs.det.lists;
import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.optimistic.OptimisticList;

import java.util.ArrayList;
import java.util.List;

public class Client6 {

    public static void main(String[] args) {
        Set set = new OptimisticList();
        int NUM_OPERATIONS = 5;
        int NUM_INSERTIONS = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        List<Integer> items = new ArrayList<>(NUM_INSERTIONS);
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            items.add(i+1);
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_INSERTIONS);
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            Integer item = items.get(i);
            threads.add(new InsertionThread(set, item));
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads.get(i).join1();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        List<DeletionThread> deleteThreads = new ArrayList<>(NUM_DELETIONS);
        for (int i = 0; i < NUM_DELETIONS; i++) {
            Integer item = items.get(i);
            deleteThreads.add(new DeletionThread(set, item));
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            deleteThreads.get(i).start();
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            try {
                deleteThreads.get(i).join1();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
