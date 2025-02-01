package org.mpisws.concurrent.programs.det.map.coarse;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Map {

    private final Node head;
    private final ReentrantLock lock = new ReentrantLock();

    public Map() {
        head = new Node(-1, -1);
    }

    public void put(int key, int value) throws JMCInterruptException {
        lock.lock();
        try {
            Node pred = head;
            Node curr = pred.next;
            while (curr != null) {
                if (curr.key == key) {
                    curr.value = value;
                    return;
                }
                pred = curr;
                curr = curr.next;
            }
            Node newNode = new Node(key, value);
            pred.next = newNode;
            newNode.next = null;
        } finally {
            lock.unlock();
        }
    }

    public void remove(int key) throws JMCInterruptException {
        lock.lock();
        try {
            Node pred = head;
            Node curr = pred.next;
            while (curr != null) {
                if (curr.key == key) {
                    pred.next = curr.next;
                    return;
                }
                pred = curr;
                curr = curr.next;
            }
        } finally {
            lock.unlock();
        }
    }

    public int get(int key) throws JMCInterruptException {
        lock.lock();
        try {
            Node curr = head.next;
            while (curr != null) {
                if (curr.key == key) {
                    return curr.value;
                }
                curr = curr.next;
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }
}
