package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped.PopperThread;
import org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped.PusherThread;
import org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped.TSStack;

import java.util.ArrayList;
import java.util.List;

public class Client6 {

    public static void main(String[] args) {
        int NUM_OPERATIONS = 3;
        long[] threadIds = new long[NUM_OPERATIONS];
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(i);
        }

        List<PusherThread> pusherThreads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            PusherThread pusherThread = new PusherThread();
            pusherThread.item = item;
            pusherThread.id = i;
            threadIds[i] = pusherThread.id;
            pusherThreads.add(pusherThread);
        }

        Stack stack = new TSStack(NUM_PUSHES, threadIds);

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).stack = stack;
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).start();
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            try {
                pusherThreads.get(i).join1();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Insertion Finished");

        List<PopperThread> popperThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            PopperThread popperThread = new PopperThread();
            popperThread.stack = stack;
            popperThreads.add(popperThread);
        }

        for (int i = 0; i < NUM_POPS; i++) {
            popperThreads.get(i).start();
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                popperThreads.get(i).join1();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Deletion Finished");
    }
}
