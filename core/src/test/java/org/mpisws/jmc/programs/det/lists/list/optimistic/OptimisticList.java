package org.mpisws.jmc.programs.det.lists.list.optimistic;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.node.FNode;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class OptimisticList implements Set {
    public FNode head;

    public OptimisticList() {
        FNode newHead = new FNode(Integer.MIN_VALUE);
        head = newHead;
        writeHead(newHead);
        readHead();
        head.setNext(new FNode(Integer.MAX_VALUE));
    }

    private void readHead() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(org.mpisws.jmc.runtime.JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/optimistic/OptimisticList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    private void writeHead(FNode newHead) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(org.mpisws.jmc.runtime.JmcRuntime.currentTask())
                        .param("newValue", newHead)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/optimistic/OptimisticList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    /**
     * @param i
     * @return
     */
    @Override
    public boolean add(int i) {
            int key = i;
            while (true) {
                FNode pred = head;
                readHead();
                FNode curr = pred.getNext();
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
                                FNode node = new FNode(i, key);
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
                FNode pred = head;
                readHead();
                FNode curr = pred.getNext();
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
            while (true) {
                FNode pred = head;
                FNode curr = pred.next;
                while (curr.key < key) {
                    pred = curr;
                    curr = curr.next;
                }
                pred.lock();
                try {
                    curr.lock();
                    try {
                        if (validate(pred, curr)) {
                            return key == curr.key;
                        }
                    } finally {
                        curr.unlock();
                    }
                } finally {
                    pred.unlock();
                }
            }
    }

    private boolean validate(FNode pred, FNode curr) {
        FNode node = head;
        readHead();
        while (node.getKey() <= pred.getKey()) {
            if (node == pred) {
                return pred.getNext() == curr;
            }
            node = node.getNext();
        }
        return false;
    }
}
