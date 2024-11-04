package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.lockFree.LockFreeStack;

import java.util.ArrayList;
import java.util.List;

public class Client2 {

    public static void main(String[] args) {
        Stack<Integer> stack = new LockFreeStack<Integer>();
        int NUM_OPERATIONS = 4;
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        List<Integer> items = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i);
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.stack = stack;
            thread.item = item;
            threads.add(thread);
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Insertion Finished");

        List<DeletionThread> popThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            Integer item = items.get(i);
            DeletionThread thread = new DeletionThread();
            thread.stack = stack;
            popThreads.add(thread);
        }

        for (int i = 0; i < NUM_POPS; i++) {
            popThreads.get(i).start();
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                popThreads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Deletion Finished");
    }
}
