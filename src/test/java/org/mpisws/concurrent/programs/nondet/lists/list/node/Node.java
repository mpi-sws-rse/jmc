package org.mpisws.concurrent.programs.nondet.lists.list.node;

import org.mpisws.concurrent.programs.nondet.lists.list.Element;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.symbolic.ConcreteInteger;

public class Node {

  public Element item;
  public Node next;

  public Node(Element item) {
    this.item = item;
  }

  public Node(int item) {
    ConcreteInteger concrete = new ConcreteInteger(item);
    this.item = new Element(concrete);
  }

  public AbstractInteger getKey() {
    return item.key;
  }
}
