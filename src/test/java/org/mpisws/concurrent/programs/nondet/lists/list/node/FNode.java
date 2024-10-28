package org.mpisws.concurrent.programs.nondet.lists.list.node;

import org.mpisws.concurrent.programs.nondet.lists.list.Element;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ConcreteInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class FNode {

    public Element item;
    public FNode next;
    public ReentrantLock lock = new ReentrantLock();

    public FNode(Element item) {
        this.item = item;
    }

    public FNode(int item) {
        ConcreteInteger concrete = new ConcreteInteger(item);
        this.item = new Element(concrete);
    }

    public void lock() throws JMCInterruptException {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public AbstractInteger getKey() {
        return item.key;
    }
}
