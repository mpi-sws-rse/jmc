package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public interface PQueue {
    void add(SymbolicInteger item, SymbolicInteger score) throws JMCInterruptException;

    SymbolicInteger removeMin() throws JMCInterruptException;
}
