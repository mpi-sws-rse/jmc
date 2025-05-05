package org.mpisws.jmc.programs.det.lists.list.fine;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.node.FNode;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class FineList implements Set {

    private final FNode head;

    public FineList() {
        FNode newNode1 = new FNode(Integer.MIN_VALUE);
        head = newNode1;
        // Write event for initializing head
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", newNode1)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/fine/FineList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);

        FNode headNode = head;
        // Read event for accessing head node
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

        FNode newNode2 = new FNode(Integer.MAX_VALUE);
        headNode.next = newNode2;
        // Write event for initializing next member of head node
        RuntimeEvent event3 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", newNode2)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "next")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", headNode)
                        .build();
        JmcRuntime.updateEventAndYield(event3);
    }


    @Override
    public boolean add(int i) {
        int key = i;
        FNode hNode = head;
        // Read event for accessing head node
        RuntimeEvent event1 =
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
        JmcRuntime.updateEventAndYield(event1);

        hNode.lock();
        FNode pred = head;
        // Read event for accessing head node
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

        try {
            FNode curr = pred.next;
            // Read event for accessing next member of head node
            RuntimeEvent event3 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                            .param("name", "next")
                            .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                            .param("instance", pred)
                            .build();
            JmcRuntime.updateEventAndYield(event3);

            curr.lock();
            try {
                int currKey = curr.key;
                // Read event for accessing key member of current node
                RuntimeEvent event4 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                .param("name", "key")
                                .param("descriptor", "I")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event4);
                while (currKey < key) {
                    pred.unlock();
                    pred = curr;
                    curr = curr.next;
                    // Read event for accessing next member of current node
                    RuntimeEvent event5 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.READ_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "next")
                                    .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                                    .param("instance", curr)
                                    .build();
                    JmcRuntime.updateEventAndYield(event5);

                    curr.lock();

                    currKey = curr.key;
                    // Read event for accessing key member of current node
                    RuntimeEvent event6 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.READ_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "key")
                                    .param("descriptor", "I")
                                    .param("instance", curr)
                                    .build();
                    JmcRuntime.updateEventAndYield(event6);
                }

                currKey = curr.key;
                // Read event for accessing key member of current node
                RuntimeEvent event7 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                .param("name", "key")
                                .param("descriptor", "I")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event7);

                if (key == currKey) {
                    return false;
                } else {
                    FNode node = new FNode(i, key);
                    node.next = curr;
                    // Write event for initializing next member of new node
                    RuntimeEvent event8 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.WRITE_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param("newValue", curr)
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "next")
                                    .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                                    .param("instance", node)
                                    .build();
                    JmcRuntime.updateEventAndYield(event8);

                    pred.next = node;
                    // Write event for initializing next member of previous node
                    RuntimeEvent event9 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.WRITE_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param("newValue", node)
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "next")
                                    .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                                    .param("instance", pred)
                                    .build();
                    JmcRuntime.updateEventAndYield(event9);
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
        FNode hNode = head;
        // Read event for accessing head node
        RuntimeEvent event1 =
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
        JmcRuntime.updateEventAndYield(event1);

        hNode.lock();
        FNode pred = head;
        // Read event for accessing head node
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

        try {
            FNode curr = pred.next;
            // Read event for accessing next member of head node
            RuntimeEvent event3 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                            .param("name", "next")
                            .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                            .param("instance", pred)
                            .build();
            JmcRuntime.updateEventAndYield(event3);

            curr.lock();
            try {
                int currKey = curr.key;
                // Read event for accessing key member of current node
                RuntimeEvent event4 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                .param("name", "key")
                                .param("descriptor", "I")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event4);

                while (currKey < key) {
                    pred.unlock();
                    pred = curr;
                    curr = curr.next;
                    // Read event for accessing next member of current node
                    RuntimeEvent event5 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.READ_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "next")
                                    .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                                    .param("instance", curr)
                                    .build();
                    JmcRuntime.updateEventAndYield(event5);

                    curr.lock();

                    currKey = curr.key;
                    // Read event for accessing key member of current node
                    RuntimeEvent event6 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.READ_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "key")
                                    .param("descriptor", "I")
                                    .param("instance", curr)
                                    .build();
                    JmcRuntime.updateEventAndYield(event6);
                }

                currKey = curr.key;
                // Read event for accessing key member of current node
                RuntimeEvent event7 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                .param("name", "key")
                                .param("descriptor", "I")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event7);

                if (key == currKey) {
                    FNode n = curr.next;
                    // Read event for accessing next member of current node
                    RuntimeEvent event8 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.READ_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "next")
                                    .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                                    .param("instance", curr)
                                    .build();
                    JmcRuntime.updateEventAndYield(event8);

                    pred.next = n;
                    // Write event for initializing next member of previous node
                    RuntimeEvent event9 =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEvent.Type.WRITE_EVENT)
                                    .taskId(JmcRuntime.currentTask())
                                    .param("newValue", n)
                                    .param(
                                            "owner",
                                            "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                                    .param("name", "next")
                                    .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                                    .param("instance", pred)
                                    .build();
                    JmcRuntime.updateEventAndYield(event9);
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
}
