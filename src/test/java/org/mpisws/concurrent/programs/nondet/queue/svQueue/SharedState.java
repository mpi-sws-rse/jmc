package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.symbolic.SymbolicInteger;

import java.util.ArrayList;

public class SharedState {

    public boolean enqueue = true;
    public boolean dequeue = false;
    public SymbolicInteger[] storedElements;

    public SharedState(int size) {
        storedElements = new SymbolicInteger[size];
    }
}
