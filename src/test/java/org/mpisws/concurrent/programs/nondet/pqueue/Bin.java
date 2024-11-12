package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public interface Bin {
    void put(SymbolicInteger item) throws JMCInterruptException;

    SymbolicInteger get() throws JMCInterruptException;
}
