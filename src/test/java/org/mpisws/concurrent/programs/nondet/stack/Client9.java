package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.lockFree.michaelScott.MSStack;
import org.mpisws.symbolic.SymbolicInteger;

import java.util.ArrayList;
import java.util.List;

public class Client9 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 4;
        Stack<SymbolicInteger> stack = new MSStack<>();

        List<SymbolicInteger> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(new SymbolicInteger("item" + i, false));
        }

        ArrayList<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            SymbolicInteger item = items.get(i);
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
                e.printStackTrace();
            }
        }
    }
}
