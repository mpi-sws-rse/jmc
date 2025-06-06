package org.mpisws.jmc.test.det.list.coarse;

import org.mpisws.jmc.test.det.list.Set;
import org.mpisws.jmc.test.det.list.node.Node;

import java.util.concurrent.locks.ReentrantLock;

/**
 * CoarseList is a coarse-grained linked list implementation of the Set interface. It uses a single
 * lock to synchronize access to the entire list, ensuring thread safety. The list supports adding,
 * removing, and checking for elements.
 */
public class CoarseList implements Set {

    /**
     * Head node of the coarse-grained linked list.
     */
    private final Node head;

    /**
     * Lock for synchronizing access to the list.
     */
    private final ReentrantLock lock;

    /**
     * Constructor to initialize the coarse-grained linked list. It creates a head node with
     * Integer.MIN_VALUE and a tail node with Integer.MAX_VALUE.
     */
    public CoarseList() {
        head = new Node(Integer.MIN_VALUE);
        head.next = new Node(Integer.MAX_VALUE);
        lock = new ReentrantLock();
    }

    /**
     * Adds an element to the set. If the element already exists, it does not add it again. This
     * method uses a coarse-grained locking mechanism to ensure thread safety. If first tries to
     * lock the list, then traverses the list to find the correct position.
     *
     * @param i the element to be added
     */
    @Override
    public boolean add(int i) {
        Node pred, curr;
        int key = i;
        try {
            lock.lock();
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
        } finally {
            // Ensure that the lock is always released, even if an exception occurs
            lock.unlock();
        }
    }

    /**
     * Removes an element from the set. If the element is not present, it does nothing. It uses a
     * coarse-grained locking mechanism to ensure thread safety. The method first locks the list,
     * then traverses the list to find the element. If the element is found, it removes it by
     * updating the next pointer of the predecessor node.
     *
     * @param i the element to be removed
     * @return true if the element was successfully removed, false if it was not present
     */
    @Override
    public boolean remove(int i) {
        Node pred, curr;
        int key = i;
        try {
            lock.lock();
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
        } finally {
            // Ensure that the lock is always released, even if an exception occurs
            lock.unlock();
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
        Node pred, curr;
        int key = i;
        try {
            lock.lock();
            pred = head;
            curr = pred.next;
            while (curr.key < key) {
                curr = curr.next;
            }
            return key == curr.key;
        } finally {
            // Ensure that the lock is always released, even if an exception occurs
            lock.unlock();
        }
    }

    @Override
    public int size() {
        int size = 0;
        try {
            lock.lock();
            Node curr = head.next;
            while (curr.key != Integer.MAX_VALUE) {
                size++;
                curr = curr.next;
            }
        } finally {
            // Ensure that the lock is always released, even if an exception occurs
            lock.unlock();
        }
        return size;
    }
}
