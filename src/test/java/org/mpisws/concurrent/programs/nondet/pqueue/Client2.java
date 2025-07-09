package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.concurrent.programs.nondet.pqueue.linear.LockBasedLinear;
import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.PropositionalFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;

public class Client2 {

    public static void main(String[] args) {
        try {
            int NUM_OPERATIONS = 4;
            int NUM_INSERTIONS = (int) Math.ceil(NUM_OPERATIONS / 2.0);
            int NUM_DELETIONS = (int) Math.floor(NUM_OPERATIONS / 2.0);
            PQueue pqueue = new LockBasedLinear(NUM_INSERTIONS);

            ArrayList<SymbolicInteger> items = new ArrayList<>(NUM_INSERTIONS);
            ArrayList<SymbolicInteger> scores = new ArrayList<>(NUM_INSERTIONS);
            for (int i = 0; i < NUM_INSERTIONS; i++) {
                SymbolicInteger item = new SymbolicInteger("item-" + i, true);
                items.add(item);
                SymbolicInteger score = new SymbolicInteger("score-" + i, true);
                ArithmeticFormula f = new ArithmeticFormula();
                SymbolicOperation op1 = f.geq(score, 0);
                SymbolicOperation op2 = f.lt(score, NUM_INSERTIONS);
                PropositionalFormula prop = new PropositionalFormula();
                SymbolicOperation op3 = prop.and(op1, op2);
                Utils.assume(op3); // ASSUME (score >= 0) && (score < NUM_OPERATIONS)
                scores.add(score);
            }

            InsertionThread[] threads = new InsertionThread[NUM_INSERTIONS];
            for (int i = 0; i < NUM_INSERTIONS; i++) {
                InsertionThread thread = new InsertionThread();
                thread.pqueue = pqueue;
                thread.item = items.get(i);
                thread.score = scores.get(i);
                threads[i] = thread;
            }

            DeletionThread[] threads1 = new DeletionThread[NUM_DELETIONS];
            for (int i = 0; i < NUM_DELETIONS; i++) {
                DeletionThread thread = new DeletionThread();
                thread.pqueue = pqueue;
                threads1[i] = thread;
            }

            for (int i = 0; i < NUM_INSERTIONS; i++) {
                threads[i].start();
            }

            for (int i = 0; i < NUM_DELETIONS; i++) {
                threads1[i].start();
            }

            for (int i = 0; i < NUM_INSERTIONS; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {

                }
            }

            for (int i = 0; i < NUM_DELETIONS; i++) {
                try {
                    threads1[i].join();
                } catch (InterruptedException e) {

                }
            }
        } catch (JMCInterruptException e) {

        }

    }
}
