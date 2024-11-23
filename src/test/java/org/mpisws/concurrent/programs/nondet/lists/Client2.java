package org.mpisws.concurrent.programs.nondet.lists;
//
// import org.mpisws.concurrent.programs.nondet.lists.list.Element;
// import org.mpisws.concurrent.programs.nondet.lists.list.Set;
// import org.mpisws.concurrent.programs.nondet.lists.list.coarse.CoarseList;
// import org.mpisws.symbolic.*;
// import org.mpisws.util.concurrent.JMCInterruptException;
// import org.mpisws.util.concurrent.Utils;
//
// import java.util.ArrayList;
// import java.util.List;
//
// public class Client2 {
//
//    public static void main(String[] args) {
//        try {
//            Set set = new CoarseList();
//            int NUM_OPERATIONS = 7;
//            int NUM_INSERTIONS = (int) Math.ceil(NUM_OPERATIONS / 2.0);
//            int NUM_DELETIONS = (int) Math.floor(NUM_OPERATIONS / 2.0);
//
//            List<Element> items = new ArrayList<>(NUM_INSERTIONS);
//            List<AbstractInteger> keys = new ArrayList<>(NUM_INSERTIONS);
//            for (int i = 0; i < NUM_INSERTIONS; i++) {
//                SymbolicInteger key = new SymbolicInteger(false);
//                ArithmeticFormula f = new ArithmeticFormula();
//                SymbolicOperation op1 = f.gt(key, Integer.MIN_VALUE);
//                SymbolicOperation op2 = f.lt(key, Integer.MAX_VALUE);
//                PropositionalFormula prop = new PropositionalFormula();
//                SymbolicOperation op3 = prop.and(op1, op2);
//                Utils.assume(op3); // ASSUME (key > Integer.MIN_VALUE) && (key <
// Integer.MAX_VALUE)
//                keys.add(key);
//                Element e = new Element(key);
//                items.add(e);
//            }
//
//            ArithmeticFormula f = new ArithmeticFormula();
//            SymbolicOperation op1 = f.distinct(keys);
//            Utils.assume(op1); // ASSUME keys are distinct
//
//            List<InsertionThread> threads = new ArrayList<>(NUM_INSERTIONS);
//            for (int i = 0; i < NUM_INSERTIONS; i++) {
//                Element item = items.get(i);
//                InsertionThread thread = new InsertionThread(set, item);
//                threads.add(thread);
//            }
//
//            for (int i = 0; i < NUM_INSERTIONS; i++) {
//                threads.get(i).start();
//            }
//
//            for (int i = 0; i < NUM_INSERTIONS; i++) {
//                try {
//                    threads.get(i).join();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            List<DeletionThread> deleteThreads = new ArrayList<>(NUM_DELETIONS);
//            for (int i = 0; i < NUM_DELETIONS; i++) {
//                Element item = items.get(i);
//                DeletionThread thread = new DeletionThread(set, item);
//                deleteThreads.add(thread);
//            }
//
//            for (int i = 0; i < NUM_DELETIONS; i++) {
//                deleteThreads.get(i).start();
//            }
//
//            for (int i = 0; i < NUM_DELETIONS; i++) {
//                try {
//                    deleteThreads.get(i).join();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            System.out.println("Deletion Finished");
//        } catch (JMCInterruptException e) {
//            System.out.println("Program Skipped");
//        }
//    }
// }
