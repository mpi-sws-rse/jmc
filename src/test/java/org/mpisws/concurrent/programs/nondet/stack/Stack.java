package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.util.concurrent.JMCInterruptException;

public interface Stack<V> {

  void push(V item) throws JMCInterruptException;

  V pop() throws JMCInterruptException;
}
