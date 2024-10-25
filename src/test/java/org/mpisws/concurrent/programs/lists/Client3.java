package org.mpisws.concurrent.programs.lists;

import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.concurrent.programs.lists.list.fine.FineList;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.SymbolicInteger;

import java.util.ArrayList;
import java.util.List;

public class Client3 {

    public static void main(String[] args) {
        Set set = new FineList();
        int NUM_OPERATIONS = 3;

        List<SymbolicInteger> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(new SymbolicInteger(false, i));
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            AbstractInteger item = items.get(i);
            threads.add(new InsertionThread(set, item));
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Insertion Finished");
    }
}
