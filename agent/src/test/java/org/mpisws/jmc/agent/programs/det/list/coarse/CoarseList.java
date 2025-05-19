package org.mpisws.jmc.agent.programs.det.list.coarse;

import org.mpisws.jmc.agent.programs.det.list.Set;
import org.mpisws.jmc.agent.programs.det.list.node.Node;

import java.util.concurrent.locks.ReentrantLock;

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
            lock.unlock();
        }
    }

    /**
     * @param i
     * @return
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
            lock.unlock();
        }
    }

    /**
     * @param i
     * @return
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
            lock.unlock();
        }
    }
}
