package org.mpi_sws.jmc.programs.nondet.stack;

public interface Stack<V> {

    void push(V item);

    V pop();
}
