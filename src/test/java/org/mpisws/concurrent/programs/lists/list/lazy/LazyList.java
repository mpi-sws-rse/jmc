package org.mpisws.concurrent.programs.lists.list.lazy;

import org.mpisws.concurrent.programs.lists.list.Node;
import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class LazyList implements Set {

    private final Node head;
    private int key = Integer.MIN_VALUE + 1;

    public LazyList() {
        head = new Node(Integer.MIN_VALUE);
        head.next = new Node(Integer.MAX_VALUE);
    }

    /**
     * @param i
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public boolean add(AbstractInteger i) throws JMCInterruptException {
        try {
            while (true) {
                Node pred = head;
                Node curr = pred.next;
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
                                i.setHash(key);
                                Node node = new Node(i, key);
                                node.next = curr;
                                pred.next = node;
                                key++;
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
            key = i.getHash();
            while (true) {
                Node pred = head;
                Node curr = pred.next;
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
        key = i.getHash();
        Node curr = head;
        while (curr.key < key) {
            curr = curr.next;
        }
        return key == curr.key && !curr.marked;
    }

    private boolean validate(Node pred, Node curr) {
        return !pred.marked && !curr.marked && pred.next == curr;
    }
}
