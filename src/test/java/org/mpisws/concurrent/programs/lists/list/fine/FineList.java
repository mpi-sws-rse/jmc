package org.mpisws.concurrent.programs.lists.list.fine;

import org.mpisws.concurrent.programs.lists.list.Node;
import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class FineList implements Set {

    private final Node head;
    private int key = Integer.MIN_VALUE + 1;

    public FineList() {
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
            head.lock();
            Node pred = head;
            try {
                Node curr = pred.next;
                curr.lock();
                try {
                    while (curr.key < key) {
                        pred.unlock();
                        pred = curr;
                        curr = curr.next;
                        curr.lock();
                    }
                    if (key == curr.key) {
                        return false;
                    } else {
                        i.setHash(key);
                        Node node = new Node(i, key);
                        key++;
                        node.next = curr;
                        pred.next = node;
                        return true;
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
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean remove(AbstractInteger i) {
        try {
            key = i.getHash();
            head.lock();
            Node pred = head;
            try {
                Node curr = pred.next;
                curr.lock();
                try {
                    while (curr.key < key) {
                        pred.unlock();
                        pred = curr;
                        curr = curr.next;
                        curr.lock();
                    }
                    if (key == curr.key) {
                        pred.next = curr.next;
                        int x = key;
                        return true;
                    } else {
                        return false;
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
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean contains(AbstractInteger i) {
        try {
            key = i.getHash();
            head.lock();
            Node pred = head;
            try {
                Node curr = pred.next;
                curr.lock();
                try {
                    while (curr.key < key) {
                        pred.unlock();
                        pred = curr;
                        curr = curr.next;
                        curr.lock();
                    }
                    return key == curr.key;
                } finally {
                    curr.unlock();
                }
            } finally {
                pred.unlock();
            }
        } catch (JMCInterruptException e) {
            return false;
        }
    }
}
