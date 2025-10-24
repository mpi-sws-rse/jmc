package org.mpi_sws.jmc.test.det.stack.lockFree.atomicTimeStamped;

import java.util.concurrent.atomic.AtomicBoolean;

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
