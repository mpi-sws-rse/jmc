package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.agmStack.AGMStack;

import java.util.ArrayList;
import java.util.List;

public class Client7 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 4;
        Stack<Integer> stack = new AGMStack<>(NUM_OPERATIONS);

        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i);
        }

        ArrayList<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.item = item;
            thread.stack = stack;
            threads.add(thread);
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }
}
