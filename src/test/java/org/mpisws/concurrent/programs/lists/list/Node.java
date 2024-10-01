package org.mpisws.concurrent.programs.lists.list;

import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ConcreteInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Node {

    public AbstractInteger item;

    public int key;

    public Node next;

    private final ReentrantLock lock = new ReentrantLock();

    public boolean marked = false;

    public Node(AbstractInteger i) {
        item = i;
        key = i.hashCode();
    }

    public Node(int i) {
        item = new ConcreteInteger(i);
        key = i;
    }

    public Node(int item, int key) {
        this.item = new ConcreteInteger(item);
        this.key = key;
    }

    public Node(AbstractInteger item, int key) {
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
