package org.mpisws.jmc.programs.nondet.stack;
//
// import org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped.PusherThread;
// import org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped.TSStack;
//
// import java.util.ArrayList;
// import java.util.List;
//
// public class Client5 {
//
//    public static void main(String[] args) {
//        int NUM_OPERATIONS = 4;
//        long[] threadIds = new long[NUM_OPERATIONS];
//        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
//        for (int i = 0; i < NUM_OPERATIONS; i++) {
//            items.add(i);
//        }
//
//        List<PusherThread> threads = new ArrayList<>(NUM_OPERATIONS);
//        for (int i = 0; i < NUM_OPERATIONS; i++) {
//            Integer item = items.get(i);
//            PusherThread thread = new PusherThread();
//            thread.item = item;
//            thread.id = i;
//            threadIds[i] = thread.id;
//            threads.add(thread);
//        }
//
//        Stack stack = new TSStack(NUM_OPERATIONS, threadIds);
//
//        for (int i = 0; i < NUM_OPERATIONS; i++) {
//            threads.get(i).stack = stack;
//        }
//
//        for (int i = 0; i < NUM_OPERATIONS; i++) {
//            threads.get(i).start();
//        }
//
//        for (int i = 0; i < NUM_OPERATIONS; i++) {
//            try {
//                threads.get(i).join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        System.out.println("Insertion Finished");
//    }
// }
