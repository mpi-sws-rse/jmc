package org.mpisws.concurrent.programs.lists.list.optimistic;

import org.mpisws.concurrent.programs.lists.list.Node;
import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class OptimisticList implements Set {

    public Node head;
    public int key = 1;

    public OptimisticList() {
        head = new Node(0);
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
        } catch (JMCInterruptException e) {
            return false;
        }
        return false;
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean contains(AbstractInteger i) {
        try {
            key = i.getHash();
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
                        return key == curr.key;
                    }
                } finally {
                    curr.unlock();
                }
            } finally {
                pred.unlock();
            }
        } catch (JMCInterruptException e) {
            return false;
        }
        return false;
    }

    private boolean validate(Node pred, Node curr) {
        Node node = head;
        while (node.key <= pred.key) {
            if (node == pred) {
                return pred.next == curr;
            }
            node = node.next;
        }
        return false;
    }
}
