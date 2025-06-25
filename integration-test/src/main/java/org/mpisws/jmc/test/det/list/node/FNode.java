package org.mpisws.jmc.test.det.list.node;

import java.util.concurrent.locks.ReentrantLock;

public class FNode {

    public int item;
    public int key;
    public FNode next;
    private final ReentrantLock lock = new ReentrantLock();

    public FNode(int i) {
        this.item = i;
        this.key = i;
    }

    public FNode(int item, int key) {
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
