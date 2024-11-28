package org.mpisws.concurrent.programs.nondet.lists;

import org.mpisws.concurrent.programs.nondet.lists.list.Element;
import org.mpisws.concurrent.programs.nondet.lists.list.Set;
import org.mpisws.concurrent.programs.nondet.lists.list.optimistic.OptimisticList;
import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.List;

public class Client5 {

    public static void main(String[] args) {
        try {
            Set set = new OptimisticList();
            int NUM_OPERATIONS = 1;

            List<Element> items = new ArrayList<>(NUM_OPERATIONS);
            List<AbstractInteger> keys = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                SymbolicInteger key = new SymbolicInteger("item" + i, false);
                ArithmeticFormula f = new ArithmeticFormula();
                SymbolicOperation op1 = f.gt(key, Integer.MIN_VALUE);
                SymbolicOperation op2 = f.lt(key, Integer.MAX_VALUE);
                PropositionalFormula prop = new PropositionalFormula();
                SymbolicOperation op3 = prop.and(op1, op2);
                Utils.assume(op3); // ASSUME (key > Integer.MIN_VALUE) && (key < Integer.MAX_VALUE)
                keys.add(key);
                Element e = new Element(key);
                items.add(e);
            }

            ArithmeticFormula f = new ArithmeticFormula();
            SymbolicOperation op1 = f.distinct(keys);
            Utils.assume(op1); // ASSUME keys are distinct

            List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                Element item = items.get(i);
                InsertionThread thread = new InsertionThread(set, item);
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
        } catch (JMCInterruptException e) {
            System.out.println("Program Skipped");
        }
    }
}
