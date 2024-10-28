package org.mpisws.concurrent.programs.det.lists.list.fine;

import org.mpisws.concurrent.programs.det.lists.list.node.FNode;
import org.mpisws.concurrent.programs.det.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class FineList implements Set {

    private final FNode head;

    public FineList() {
        head = new FNode(Integer.MIN_VALUE);
        head.next = new FNode(Integer.MAX_VALUE);
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
            head.lock();
            FNode pred = head;
            try {
                FNode curr = pred.next;
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
                        FNode node = new FNode(i, key);
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
            int key = i.getHash();
            head.lock();
            FNode pred = head;
            try {
                FNode curr = pred.next;
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
            int key = i.getHash();
            head.lock();
            FNode pred = head;
            try {
                FNode curr = pred.next;
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
