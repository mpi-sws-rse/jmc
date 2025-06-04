package org.mpisws.jmc.programs.det.lists.list.coarse;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.node.Node;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;

public class CoarseList implements Set {

    private final Node head;
    private final JmcReentrantLock lock;

    private void readHead() {
        JmcRuntimeUtils.readEvent(
                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList",
                "head",
                "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;",
                this);
    }

    private void writeHead(Node newNode) {
        JmcRuntimeUtils.writeEvent(
                newNode,
                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList",
                "head",
                "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;",
                this);
    }

    private void readLock() {
        JmcRuntimeUtils.readEvent(
                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList",
                "lock",
                "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;",
                this);
    }

    private void writeLock(JmcReentrantLock newLock) {
        JmcRuntimeUtils.writeEvent(
                newLock,
                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList",
                "lock",
                "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;",
                this);
    }

    public CoarseList() {
        Node newNode1 = new Node(Integer.MIN_VALUE);
        head = newNode1;
        // Write event for initializing head node
        writeHead(newNode1);

        Node headNode = head;
        // Read event for accessing head node
        readHead();

        Node newNode2 = new Node(Integer.MAX_VALUE);
        headNode.setNext(newNode2);

        JmcReentrantLock lock = new JmcReentrantLock();
        this.lock = lock;
        // Write event for initializing lock
        writeLock(lock);
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean add(int i) {
        Node pred, curr;
        int key = i;
        JmcReentrantLock l = lock;
        // Read event to read the lock object
        readLock();

        //        try {
        //            l.lock();
        Node h = head;
        // Read event to read the head node
        readHead();
        pred = h;

        curr = pred.getNext();

        int currKey = curr.getKey();

        while (currKey < key) {
            pred = curr;
            Node n = curr.getNext();
            curr = n;

            currKey = curr.getKey();
        }

        currKey = curr.getKey();
        if (key == currKey) {
            return false;
        } else {
            Node node = new Node(i, key);
            node.setNext(curr);

            pred.setNext(node);

            return true;
        }
        //        } finally {
        //            l.unlock();
        //        }
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean remove(int i) {
        Node pred, curr;
        int key = i;
        JmcReentrantLock l = lock;
        readLock();
        //        try {
        //            l.lock();
        pred = head;
        readHead();
        curr = pred.getNext();

        int currKey = curr.getKey();
        while (currKey < key) {
            pred = curr;

            Node n = curr.getNext();
            curr = n;

            currKey = curr.getKey();
        }
        currKey = curr.getKey();
        if (key == currKey) {
            Node n = curr.getNext();

            pred.setNext(n);
            return true;
        } else {
            return false;
        }
        //        } finally {
        //            l.unlock();
        //        }
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
            readLock();
            pred = head;
            readHead();
            curr = pred.getNext();
            while (curr.getKey() < key) {
                curr = curr.getNext();
            }
            return key == curr.getKey();
        } finally {
            lock.unlock();
        }
    }
}
