package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped.PoperThread;
import org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped.PusherThread2;
import org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped.TSStack;

import java.util.ArrayList;
import java.util.List;

public class Client15 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 4;
        int NUM_PUSH_PER_THREAD = 2;
        long[] threadIds = new long[NUM_OPERATIONS];
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);
        int elm = 0;
        List<int[]> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_PUSHES; i++) {
            int[] elms = new int[NUM_PUSH_PER_THREAD];
            for (int j = 0; j < NUM_PUSH_PER_THREAD; j++) {
                elms[j] = elm++;
            }
            items.add(elms);
        }

        List<PusherThread2> pusherThreads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            int[] item = items.get(i);
            PusherThread2 pusherThread = new PusherThread2();
            pusherThread.item = item;
            pusherThread.id = i;
            threadIds[i] = pusherThread.id;
            pusherThreads.add(pusherThread);
        }

        Stack stack = new TSStack(NUM_PUSHES, threadIds);

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).stack = stack;
        }

        List<PoperThread> poperThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            PoperThread poperThread = new PoperThread();
            poperThread.stack = stack;
            poperThread.id = i + NUM_PUSHES;
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
                e.printStackTrace();
            }
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                poperThreads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
