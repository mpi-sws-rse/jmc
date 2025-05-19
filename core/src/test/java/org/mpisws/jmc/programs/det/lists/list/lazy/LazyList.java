package org.mpisws.jmc.programs.det.lists.list.lazy;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.node.LNode;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class LazyList implements Set {

    private final LNode head;

    public LazyList() {
        LNode newHead = new LNode(Integer.MIN_VALUE);
        head = newHead;
        writeHead(newHead);
        LNode curHead = head;
        readHead();
        curHead.setNext(new LNode(Integer.MAX_VALUE));
    }

    private void writeHead(LNode node) {
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", node)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/lazy/LazyList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/LNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);
    }

    private void readHead() {
        RuntimeEvent event2 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/lazy/LazyList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/LNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);
    }

    @Override
    public boolean add(int i) {
            int key = i;
            while (true) {
                LNode pred = head;
                readHead();
                LNode curr = pred.getNext();
                while (curr.getKey() < key) {
                    pred = curr;
                    curr = curr.getNext();
                }
                pred.lock();
                try {
                    curr.lock();
                    try {
                        if (validate(pred, curr)) {
                            if (key == curr.getKey()) {
                                return false;
                            } else {
                                LNode node = new LNode(i, key);
                                node.setNext(curr);
                                pred.setNext(node);
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

    /**
     * @param i
     * @return
     */
    @Override
    public boolean remove(int i) {
            int key = i;
            while (true) {
                LNode pred = head;
                readHead();
                LNode curr = pred.getNext();
                while (curr.getKey() < key) {
                    pred = curr;
                    curr = curr.getNext();
                }
                pred.lock();
                try {
                    curr.lock();
                    try {
                        if (validate(pred, curr)) {
                            if (key == curr.getKey()) {
                                curr.setMarked(true);
                                pred.setNext(curr.getNext());
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

    /**
     * @param i
     * @return
     */
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
        return !pred.getMarked() && !curr.getMarked() && pred.getNext() == curr;
    }
}
