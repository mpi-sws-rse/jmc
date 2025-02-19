package org.mpisws.concurrent.programs.det.lists.list.coarse;

import org.mpisws.concurrent.programs.det.lists.list.node.Node;
import org.mpisws.concurrent.programs.det.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.ReentrantLock;

public class CoarseList implements Set {

    private final Node head;
    private final ReentrantLock lock;

    public CoarseList() {
        head = new Node(Integer.MIN_VALUE);
        head.next = new Node(Integer.MAX_VALUE);
        lock = new ReentrantLock();
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean add(AbstractInteger i) {
        Node pred, curr;
        int key = i.getHash();
        synchronized (lock) {
            pred = head;
            curr = pred.next;
            while (curr.key < key) {
                pred = curr;
                curr = curr.next;
            }
            if (key == curr.key) {
                return false;
            } else {
                Node node = new Node(i, key);
                node.next = curr;
                pred.next = node;
                return true;
            }
        }
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean remove(AbstractInteger i) {
        Node pred, curr;
        int key = i.getHash();
        synchronized (lock) {
            pred = head;
            curr = pred.next;
            while (curr.key < key) {
                pred = curr;
                curr = curr.next;
            }
            if (key == curr.key) {
                pred.next = curr.next;
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean contains(AbstractInteger i) {
        Node pred, curr;
        int key = i.getHash();
        synchronized (lock) {
            pred = head;
            curr = pred.next;
            while (curr.key < key) {
                curr = curr.next;
            }
            return key == curr.key;
        }
    }
}
