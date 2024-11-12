package org.mpisws.concurrent.programs.nondet.pqueue.linear;

import org.mpisws.concurrent.programs.nondet.pqueue.Bin;
import org.mpisws.concurrent.programs.nondet.pqueue.LockBasedBin;
import org.mpisws.concurrent.programs.nondet.pqueue.PQueue;
import org.mpisws.symbolic.SymbolicArray;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class LockBasedLinear implements PQueue {

    int range;
    SymbolicArray<Bin> pqueue;
    Bin[] pqueue1;

    public LockBasedLinear(int range) throws JMCInterruptException {
        this.range = range;
        pqueue = new SymbolicArray<Bin>(range);
        for (int i = 0; i < range; i++) {
            pqueue.set(i, new LockBasedBin());
        }
    }

    public void add(SymbolicInteger item, SymbolicInteger score) throws JMCInterruptException {
        pqueue.get(score).put(item);
    }

    public SymbolicInteger removeMin() throws JMCInterruptException {
        for (int i = 0; i < range; i++) {
            SymbolicInteger item = pqueue.get(i).get();
            if (item != null) {
                return item;
            }
        }
        return null;
    }
}
