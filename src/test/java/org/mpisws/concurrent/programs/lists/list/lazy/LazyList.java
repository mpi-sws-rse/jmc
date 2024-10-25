package org.mpisws.concurrent.programs.lists.list.lazy;

import org.mpisws.concurrent.programs.lists.list.node.LNode;
import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class LazyList implements Set {

    private final LNode head;

    public LazyList() {
        head = new LNode(Integer.MIN_VALUE);
        head.next = new LNode(Integer.MAX_VALUE);
    }

    /**
     * @param i
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public boolean add(AbstractInteger i) throws JMCInterruptException {
        try {
            int key = i.getHash();
            while (true) {
                LNode pred = head;
                LNode curr = pred.next;
                while (curr.key < key) {
                    pred = curr;
                    curr = curr.next;
                }
                pred.lock();
                try {
                    curr.lock();
                    try {
                        if (validate(pred, curr)) {
                            if (key == curr.key) {
                                return false;
                            } else {
                                LNode node = new LNode(i, key);
                                node.next = curr;
                                pred.next = node;
                                return true;
                            }
                        }
                    } finally {
                        curr.unlock();
                    }
                } finally {
                    pred.unlock();
                }
            }
        } catch (JMCInterruptException e) {
            return false;
        }
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean remove(AbstractInteger i) {
        try {
            int key = i.getHash();
            while (true) {
                LNode pred = head;
                LNode curr = pred.next;
                while (curr.key < key) {
                    pred = curr;
                    curr = curr.next;
                }
                pred.lock();
                try {
                    curr.lock();
                    try {
                        if (validate(pred, curr)) {
                            if (key == curr.key) {
                                curr.marked = true;
                                pred.next = curr.next;
                                return true;
                            } else {
                                return false;
                            }
                        }
                    } finally {
                        curr.unlock();
                    }
                } finally {
                    pred.unlock();
                }
            }
        } catch (JMCInterruptException e) {
            return false;
        }
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean contains(AbstractInteger i) {
        int key = i.getHash();
        LNode curr = head;
        while (curr.key < key) {
            curr = curr.next;
        }
        return key == curr.key && !curr.marked;
    }

    private boolean validate(LNode pred, LNode curr) {
        return !pred.marked && !curr.marked && pred.next == curr;
    }
}
