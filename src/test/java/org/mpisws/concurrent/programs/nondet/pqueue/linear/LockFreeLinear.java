package org.mpisws.concurrent.programs.nondet.pqueue.linear;

import org.mpisws.concurrent.programs.nondet.pqueue.Bin;
import org.mpisws.concurrent.programs.nondet.pqueue.LockFreeBin;
import org.mpisws.concurrent.programs.nondet.pqueue.PQueue;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class LockFreeLinear implements PQueue {

    int range;
    Bin[] pqueue;

    public LockFreeLinear(int range) {
        this.range = range;
        pqueue = new Bin[range];
        for (int i = 0; i < range; i++) {
            pqueue[i] = new LockFreeBin();
        }
    }

    public void add(SymbolicInteger item, SymbolicInteger score) throws JMCInterruptException {
        pqueue[score.getIntValue()].put(item);
    }

    public SymbolicInteger removeMin() throws JMCInterruptException {
        for (int i = 0; i < range; i++) {
            SymbolicInteger item = pqueue[i].get();
            if (item != null) {
                return item;
            }
        }
        return null;
    }
}
