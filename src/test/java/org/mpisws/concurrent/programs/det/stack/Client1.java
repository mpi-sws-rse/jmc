package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.lockFree.LockFreeStack;

import java.util.ArrayList;
import java.util.List;

public class Client1 {

    public static void main(String[] args) {

        Stack stack = new LockFreeStack<Integer>();
        int NUM_OPERATIONS = 6;

        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i);
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.stack = stack;
            thread.item = item;
            threads.add(thread);
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
