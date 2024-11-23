package org.mpisws.concurrent.programs.det.lists;
//
// import org.mpisws.concurrent.programs.det.lists.list.Set;
// import org.mpisws.concurrent.programs.det.lists.list.lazy.LazyList;
// import org.mpisws.symbolic.AbstractInteger;
// import org.mpisws.symbolic.SymbolicInteger;
//
// import java.util.ArrayList;
// import java.util.List;
//
// public class Client8 {
//
//    public static void main(String[] args) {
//        Set set = new LazyList();
//        int NUM_OPERATIONS = 5;
//        int NUM_INSERTIONS = (int) Math.ceil(NUM_OPERATIONS / 2.0);
//        int NUM_DELETIONS = (int) Math.floor(NUM_OPERATIONS / 2.0);
//
//        List<SymbolicInteger> items = new ArrayList<>(NUM_INSERTIONS);
//        for (int i = 0; i < NUM_INSERTIONS; i++) {
//            items.add(new SymbolicInteger(false, i));
//        }
//
//        List<InsertionThread> threads = new ArrayList<>(NUM_INSERTIONS);
//        for (int i = 0; i < NUM_INSERTIONS; i++) {
//            AbstractInteger item = items.get(i);
//            threads.add(new InsertionThread(set, item));
//        }
//
//        for (int i = 0; i < NUM_INSERTIONS; i++) {
//            threads.get(i).start();
//        }
//
//        for (int i = 0; i < NUM_INSERTIONS; i++) {
//            try {
//                threads.get(i).join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        List<DeletionThread> deleteThreads = new ArrayList<>(NUM_DELETIONS);
//        for (int i = 0; i < NUM_DELETIONS; i++) {
//            AbstractInteger item = items.get(i);
//            deleteThreads.add(new DeletionThread(set, item));
//        }
//
//        for (int i = 0; i < NUM_DELETIONS; i++) {
//            deleteThreads.get(i).start();
//        }
//
//        for (int i = 0; i < NUM_DELETIONS; i++) {
//            try {
//                deleteThreads.get(i).join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        System.out.println("Deletion Finished");
//    }
// }
