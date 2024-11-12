package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.concurrent.programs.nondet.pqueue.linear.LockBasedLinear;
import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.PropositionalFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;

public class Client1 {

    public static void main(String[] args) {
        try {
            int NUM_OPERATIONS = 5;
            PQueue pqueue = new LockBasedLinear(NUM_OPERATIONS);

            ArrayList<SymbolicInteger> items = new ArrayList<>(NUM_OPERATIONS);
            ArrayList<SymbolicInteger> scores = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                SymbolicInteger item = new SymbolicInteger(false);
                items.add(item);
                SymbolicInteger score = new SymbolicInteger(false);
                ArithmeticFormula f = new ArithmeticFormula();
                SymbolicOperation op1 = f.geq(score, 0);
                SymbolicOperation op2 = f.lt(score, NUM_OPERATIONS);
                PropositionalFormula prop = new PropositionalFormula();
                SymbolicOperation op3 = prop.and(op1, op2);
                Utils.assume(op3); // ASSUME (score >= 0) && (score < NUM_OPERATIONS)
                scores.add(score);
            }

            InsertionThread[] threads = new InsertionThread[NUM_OPERATIONS];
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                InsertionThread thread = new InsertionThread();
                thread.pqueue = pqueue;
                thread.item = items.get(i);
                thread.score = scores.get(i);
                threads[i] = thread;
            }

            for (int i = 0; i < NUM_OPERATIONS; i++) {
                threads[i].start();
            }

            for (int i = 0; i < NUM_OPERATIONS; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {

                }
            }
        } catch (JMCInterruptException e) {

        }

    }
}
