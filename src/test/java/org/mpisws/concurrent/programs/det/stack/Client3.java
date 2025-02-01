package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.lockFree.elimination.EliminationBackoffStack;

import java.util.ArrayList;
import java.util.List;

public class Client3 {

    public static void main(String[] args) {
        int SIZE = args.length;
        int[] arr = new int[SIZE]; // Over data domain
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }
        Stack stack = new EliminationBackoffStack<Integer>(SIZE);

        List<Integer> items = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            items.add(i);
        }

        List<InsertionThread> threads = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.stack = stack;
            thread.item = item;
            thread.index = arr[i];
            threads.add(thread);
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
    }
}
