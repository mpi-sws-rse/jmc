package org.mpisws.jmc.test.det.list.lazy;

import org.mpisws.jmc.test.det.list.Set;
import org.mpisws.jmc.test.det.list.node.LNode;

public class LazyList implements Set {

    private final LNode head;

    public LazyList() {
        head = new LNode(Integer.MIN_VALUE);
        head.next = new LNode(Integer.MAX_VALUE);
    }

    @Override
    public boolean add(int i) {
        int key = i;
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
    }


    @Override
    public boolean remove(int i) {
        int key = i;
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
    }

    @Override
    public boolean contains(int i) {
        int key = i;
        LNode curr = head;
        while (curr.key < key) {
            curr = curr.next;
        }
        return key == curr.key && !curr.marked;
    }

    private boolean validate(LNode pred, LNode curr) {
        return !pred.marked && !curr.marked && pred.next == curr;
    }

    /**
     * Returns the number of elements in the set.
     *
     * @return the size of the set
     */
    @Override
    public int size() {
        int size = 0;
        LNode curr = head.next;
        while (curr.key != Integer.MAX_VALUE) {
            if (!curr.marked) {
                size++;
            }
            curr = curr.next;
        }
        return size;
    }
}
