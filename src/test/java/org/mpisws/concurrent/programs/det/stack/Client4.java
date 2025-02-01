package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.lockFree.elimination.EliminationBackoffStack;

import java.util.ArrayList;
import java.util.List;

public class Client4 {

    public static void main(String[] args) {

        int NUM_OPERATIONS = 4;
        int SIZE = args.length;
        int[] arr = new int[SIZE]; // Over data domain
        for (int i = 0; i < SIZE; i++) {
            arr[i] = Integer.parseInt(args[i]);
        }
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);
        Stack stack = new EliminationBackoffStack<Integer>(NUM_PUSHES);

        List<Integer> items = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(i);
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.stack = stack;
            thread.item = item;
            thread.index = arr[i];
            threads.add(thread);
        }

        List<DeletionThread> popThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            Integer item = items.get(i);
            DeletionThread thread = new DeletionThread();
            thread.stack = stack;
            thread.item = item;
            thread.index = arr[i + NUM_PUSHES];
            popThreads.add(thread);
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_POPS; i++) {
            popThreads.get(i).start();
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                popThreads.get(i).join();
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }
    }
}
