package org.mpisws.concurrent.programs.det.stack.lockFree;

import org.mpisws.concurrent.programs.det.stack.Backoff;
import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class LockFreeStack<V> implements Stack<V> {

  public final int MIN_DELAY = 1;
  public final int MAX_DELAY = 10;
  public AtomicReference<Node<V>> top = new AtomicReference<>(null);
  public Backoff backoff = new Backoff(MIN_DELAY, MAX_DELAY);

  protected boolean tryPush(Node<V> node) throws JMCInterruptException {
    Node<V> oldTop = top.get();
    node.next = oldTop;
    return top.compareAndSet(oldTop, node);
  }

  @Override
  public void push(V value) throws JMCInterruptException {
    Node<V> node = new Node<>(value);
    while (!tryPush(node)) {
      backoff.backoff();
    }
  }

  protected Node<V> tryPop() throws JMCInterruptException {
    Node<V> oldTop = top.get();
    if (oldTop == null) {
      return null;
    }
    Node<V> newTop = oldTop.next;
    if (top.compareAndSet(oldTop, newTop)) {
      return oldTop;
    } else {
      return null;
    }
  }

  @Override
  public V pop() throws JMCInterruptException {
    while (true) {
      Node<V> returnNode = tryPop();
      if (returnNode != null) {
        return returnNode.value;
      } else {
        backoff.backoff();
      }
    }
  }
}
