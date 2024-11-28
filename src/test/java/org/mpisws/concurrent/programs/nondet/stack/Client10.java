package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.lockFree.michaelScott.MSStack;
import org.mpisws.symbolic.SymbolicInteger;

import java.util.ArrayList;
import java.util.List;

public class Client10 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 4;
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        Stack<SymbolicInteger> stack = new MSStack<>();

        List<SymbolicInteger> items = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(new SymbolicInteger("item-" + i, false));
        }

        List<InsertionThread> pusherThreads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            SymbolicInteger item = items.get(i);
            InsertionThread pusherThread = new InsertionThread();
            pusherThread.item = item;
            pusherThread.stack = stack;
            pusherThreads.add(pusherThread);
        }

        List<DeletionThread> poperThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            DeletionThread poperThread = new DeletionThread();
            poperThread.stack = stack;
            poperThreads.add(poperThread);
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).start();
        }

        for (int i = 0; i < NUM_POPS; i++) {
            poperThreads.get(i).start();
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            try {
                pusherThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                poperThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }
}
