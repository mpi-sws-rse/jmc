package org.mpisws.concurrent.programs.det.stack.lockFree.simpleTSStack;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.AtomicBoolean;

public class TNode<T> {

    public T value;
    public TNode<T> next;
    public int timeStamp;
    public AtomicBoolean taken;

    /**
     * @param value
     */
    public TNode(T value, boolean taken) {
        this.value = value;
        this.taken = new AtomicBoolean(taken);
        this.next = null;
        this.timeStamp = -1;
    }
}
