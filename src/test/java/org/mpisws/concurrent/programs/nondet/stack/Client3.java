package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.lockFree.elimination.EliminationBackoffStack;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.ArrayList;
import java.util.List;

public class Client3 {

    public static void main(String[] args) {

        try {
            int NUM_OPERATIONS = 2;
            Stack<SymbolicInteger> stack = new EliminationBackoffStack(NUM_OPERATIONS);

            List<SymbolicInteger> items = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                items.add(new SymbolicInteger("item-" + i, false));
            }

            List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                SymbolicInteger item = items.get(i);
                InsertionThread thread = new InsertionThread();
                thread.stack = stack;
                thread.item = item;
                thread.index = new SymbolicInteger("Iindex-" + i, false);
                threads.add(thread);
            }

            for (int i = 0; i < NUM_OPERATIONS; i++) {
                threads.get(i).start();
            }

            for (int i = 0; i < NUM_OPERATIONS; i++) {
                try {
                    threads.get(i).join();
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                }
            }

            //System.out.println("Insertion Finished");
        } catch (JMCInterruptException e) {
            //System.out.println("JMCInterruptException in main");
        }
    }
}
