package org.mpisws.concurrent.programs.det.lists;

import org.mpisws.concurrent.programs.det.lists.list.Set;
import org.mpisws.concurrent.programs.det.lists.list.coarse.CoarseList;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.SymbolicInteger;


import java.util.ArrayList;
import java.util.List;

public class Client1 {

    public static void main(String[] args) {
        int SIZE = args.length;
        int[] arr = new int[SIZE]; // Data domain is {0,1,2}
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }

        Set set = new CoarseList();

        List<SymbolicInteger> items = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            items.add(new SymbolicInteger(false, arr[i]));
        }

        List<InsertionThread> threads = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            AbstractInteger item = items.get(i);
            threads.add(new InsertionThread(set, item));
        }

        for (int i = 0; i < SIZE; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < SIZE; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }

        //System.out.println("Insertion Finished");
    }
}
