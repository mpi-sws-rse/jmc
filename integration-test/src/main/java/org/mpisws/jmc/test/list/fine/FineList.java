package org.mpisws.jmc.test.list.fine;

import org.mpisws.jmc.test.list.Set;
import org.mpisws.jmc.test.list.node.FNode;

public class FineList implements Set {

    private final FNode head;

    public FineList() {
        head = new FNode(Integer.MIN_VALUE);
        head.next = new FNode(Integer.MAX_VALUE);
    }

    /**
     * Adds an element to the set.
     *
     * @param i the element to be added
     */
    @Override
    public boolean add(int i) {
        int key = i;
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
    }

    /**
     * Removes an element from the set.
     *
     * @param i the element to be removed
     * @return true if the element was successfully removed, false if it was not present
     */
    @Override
    public boolean remove(int i) {
        int key = i;
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
    }

    /**
     * Checks if the set contains a specific element.
     *
     * @param i the element to check for
     * @return true if the element is present in the set, false otherwise
     */
    @Override
    public boolean contains(int i) {
        int key = i;
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
    }

    // TODO: Check if this is correct
    @Override
    public int size() {
        int size = 0;
        head.lock();
        FNode pred = head;
        try {
            FNode curr = pred.next;
            curr.lock();
            try {
                while (curr.key < Integer.MAX_VALUE) {
                    size++;
                    pred.unlock();
                    pred = curr;
                    curr = curr.next;
                    curr.lock();
                }
            } finally {
                curr.unlock();
            }
        } finally {
            pred.unlock();
        }
        return size;
    }
}
