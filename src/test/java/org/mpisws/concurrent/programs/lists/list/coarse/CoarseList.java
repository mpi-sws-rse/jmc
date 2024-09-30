package org.mpisws.concurrent.programs.lists.list.coarse;

import org.mpisws.concurrent.programs.lists.list.Node;
import org.mpisws.concurrent.programs.lists.list.Set;
import org.mpisws.symbolic.AbstractInteger;

public class CoarseList implements Set {

    private final Node head;
    private final Object lock;
    private int key = Integer.MIN_VALUE + 1;

    public CoarseList() {
        head = new Node(Integer.MIN_VALUE);
        head.next = new Node(Integer.MAX_VALUE);
        lock = new Object();
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean add(AbstractInteger i) {
        Node pred, curr;
        //int key = i.hashCode();
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
                i.setHash(key);
                Node node = new Node(i, key);
                key++;
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
