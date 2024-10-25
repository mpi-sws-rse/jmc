package org.mpisws.concurrent.programs.lists.list.node;

import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ConcreteInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class LNode {

    public AbstractInteger item;
    public int key;
    public LNode next;
    private final ReentrantLock lock = new ReentrantLock();
    public boolean marked = false;

    public LNode(AbstractInteger i) {
        item = i;
        key = i.getHash();
    }

    public LNode(int i) {
        item = new ConcreteInteger(i);
        key = i;
    }

    public LNode(int item, int key) {
        this.item = new ConcreteInteger(item);
        this.key = key;
    }

    public LNode(AbstractInteger item, int key) {
        this.item = item;
        this.key = key;
    }

    public void lock() throws JMCInterruptException {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
