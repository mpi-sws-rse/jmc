package org.mpisws.concurrent.programs.nondet.lists.list.node;

import org.mpisws.concurrent.programs.nondet.lists.list.Element;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ConcreteInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class LNode {

  public Element item;
  public LNode next;
  public ReentrantLock lock = new ReentrantLock();
  public boolean marked = false;

  public LNode(Element i) {
    item = i;
  }

  public LNode(int i) {
    ConcreteInteger concrete = new ConcreteInteger(i);
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
