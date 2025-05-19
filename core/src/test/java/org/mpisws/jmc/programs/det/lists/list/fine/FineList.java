package org.mpisws.jmc.programs.det.lists.list.fine;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.node.FNode;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class FineList implements Set {

    private FNode head;

    private FNode readHead() {
        FNode node = head;
        RuntimeEvent event2 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/fine/FineList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);
        return node;
    }

    private void setHead(FNode head) {
        this.head = head;
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", head)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/fine/FineList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);
    }

    public FineList() {
        FNode newNode1 = new FNode(Integer.MIN_VALUE);
        setHead(newNode1);
        // Write event for initializing head

        FNode headNode = readHead();

        FNode newNode2 = new FNode(Integer.MAX_VALUE);
        headNode.setNext(newNode2);
    }


    @Override
    public boolean add(int i) {
        int key = i;
        FNode hNode = readHead();

        hNode.lock();
        FNode pred = hNode;

        try {
            FNode curr = pred.getNext();

            curr.lock();
            try {
                int currKey = curr.getKey();
                while (currKey < key) {
                    pred.unlock();
                    pred = curr;
                    curr = curr.getNext();

                    curr.lock();

                    currKey = curr.getKey();
                }

                currKey = curr.getKey();

                if (key == currKey) {
                    return false;
                } else {
                    FNode node = new FNode(i, key);
                    node.setNext(curr);

                    pred.setNext(node);
                    return true;
                }
            } finally {
                curr.unlock();
            }
        } finally {
            pred.unlock();
        }
    }


    @Override
    public boolean remove(int i) {
        int key = i;
        FNode hNode = readHead();

        hNode.lock();
        FNode pred = hNode;

        try {
            FNode curr = pred.getNext();

            curr.lock();
            try {
                int currKey = curr.getKey();

                while (currKey < key) {
                    pred.unlock();
                    pred = curr;
                    curr = curr.getNext();

                    curr.lock();

                    currKey = curr.getKey();
                }

                currKey = curr.getKey();

                if (key == currKey) {
                    FNode n = curr.getNext();

                    pred.setNext(n);
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


    @Override
    public boolean contains(int i) {
        int key = i;
        head.lock();
        FNode pred = head;
        try {
            FNode curr = pred.getNext();
            curr.lock();
            try {
                while (curr.getKey() < key) {
                    pred.unlock();
                    pred = curr;
                    curr = curr.getNext();
                    curr.lock();
                }
                return key == curr.getKey();
            } finally {
                curr.unlock();
            }
        } finally {
            pred.unlock();
        }
    }
}
