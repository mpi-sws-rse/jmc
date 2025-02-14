package org.mpisws.concurrent.programs.nondet.stack;

public interface Stack<V> {

    void push(V item);

    V pop();
}
