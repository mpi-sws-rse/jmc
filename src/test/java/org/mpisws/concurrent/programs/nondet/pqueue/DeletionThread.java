package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread extends Thread {

    public PQueue pqueue;
    public SymbolicInteger item;

    public DeletionThread(PQueue pqueue) {
        this.pqueue = pqueue;
    }

    public DeletionThread() {
    }

    public void run() {
        try {
            item = pqueue.removeMin();
        } catch (JMCInterruptException e) {
        }
    }
}
