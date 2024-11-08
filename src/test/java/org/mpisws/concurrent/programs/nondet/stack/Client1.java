package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.lockFree.LockFreeStack;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.ArrayList;
import java.util.List;

public class Client1 {

    public static void main(String[] args) {

        try {
            Stack stack = new LockFreeStack<SymbolicInteger>();
            int NUM_OPERATIONS = 3;

            List<SymbolicInteger> items = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                SymbolicInteger item = new SymbolicInteger(false);
                items.add(item);
            }

            List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                SymbolicInteger item = items.get(i);
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
        } catch (JMCInterruptException e) {
            System.out.println("Interrupted");
        }
    }
}
