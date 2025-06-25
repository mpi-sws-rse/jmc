package org.mpisws.jmc.test.det.list.node;

import java.util.concurrent.locks.ReentrantLock;

public class LNode {

    public int item;
    public int key;
    public LNode next;
    private final ReentrantLock lock = new ReentrantLock();
    public boolean marked = false;

    public LNode(int i) {
        item = i;
        key = i;
    }

    public LNode(int item, int key) {
        this.item = item;
        this.key = key;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
