package org.mpisws.jmc.programs.det.stack.lockFree.timeStamped;

import org.mpisws.jmc.util.concurrent.JmcAtomicBoolean;

public class TNode<T> {

    public T value;
    public TNode<T> next;
    public TimeStamp timeStamp;
    public JmcAtomicBoolean taken;

    /**
     * @param value
     */
    public TNode(T value, boolean taken) {
        this.value = value;
        this.taken = new JmcAtomicBoolean(taken);
        this.next = null;
        this.timeStamp = new TimeStamp(-1);
    }
}
