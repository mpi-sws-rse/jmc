package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.SymbolicInteger;

public class SharedState {

    public boolean enqueue = true;
    public boolean dequeue = false;
    public SymbolicInteger[] storedElements;

    public SharedState(int size) {
        storedElements = new SymbolicInteger[size];
    }
}
