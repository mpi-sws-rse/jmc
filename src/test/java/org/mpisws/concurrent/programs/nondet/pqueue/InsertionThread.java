package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    public PQueue pqueue;
    public SymbolicInteger item;
    public SymbolicInteger score;

    public InsertionThread(PQueue pqueue, SymbolicInteger item, SymbolicInteger score) {
        this.pqueue = pqueue;
        this.item = item;
        this.score = score;
    }

    public InsertionThread() {
    }

    public void run() {
        try {
            pqueue.add(item, score);
        } catch (JMCInterruptException e) {
        }
    }
}
