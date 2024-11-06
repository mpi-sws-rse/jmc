package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public interface Queue {

    void enq(SymbolicInteger x) throws JMCInterruptException;

    SymbolicInteger deq() throws JMCInterruptException;
}
