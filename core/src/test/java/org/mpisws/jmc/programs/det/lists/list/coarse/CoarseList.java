package org.mpisws.jmc.programs.det.lists.list.coarse;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.programs.det.lists.list.node.Node;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;

public class CoarseList implements Set {

    private final Node head;
    private final JmcReentrantLock lock;

    public CoarseList() {
        Node newNode1 = new Node(Integer.MIN_VALUE);
        head = newNode1;
        // Write event for initializing head node
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", newNode1)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);

        Node headNode = head;
        // Read event for accessing head node
        RuntimeEvent event2 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList")
                        .param("name", "head")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);

        Node newNode2 = new Node(Integer.MAX_VALUE);
        headNode.next = newNode2;
        // Write event for initializing next member of head node
        RuntimeEvent event3 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", newNode2)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/Node")
                        .param("name", "next")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                        .param("instance", headNode)
                        .build();
        JmcRuntime.updateEventAndYield(event3);

        JmcReentrantLock lock = new JmcReentrantLock();
        this.lock = lock;
        // Write event for initializing lock
        RuntimeEvent event4 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", lock)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList")
                        .param("name", "lock")
                        .param("descriptor", "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event4);
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
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList")
                        .param("name", "lock")
                        .param("descriptor", "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);

        try {
            l.lock();
            Node h = head;
            // Read event to read the head node
            RuntimeEvent event1 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList")
                            .param("name", "head")
                            .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event1);
            pred = h;

            curr = pred.next;
            // Read event to read the next member of the head node
            RuntimeEvent event2 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/Node")
                            .param("name", "next")
                            .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                            .param("instance", pred)
                            .build();
            JmcRuntime.updateEventAndYield(event2);

            int currKey = curr.key;
            // Read event to read the key of the current node
            RuntimeEvent event3 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/Node")
                            .param("name", "key")
                            .param("descriptor", "I")
                            .param("instance", curr)
                            .build();
            JmcRuntime.updateEventAndYield(event3);

            while (currKey < key) {
                pred = curr;
                Node n = curr.next;
                // Read event to read the next member of the current node
                RuntimeEvent event4 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "next")
                                .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event4);
                curr = n;

                currKey = curr.key;
                // Read event to read the key of the current node
                RuntimeEvent event5 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "key")
                                .param("descriptor", "I")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event5);
            }

            currKey = curr.key;
            // Read event to read the key of the current node
            RuntimeEvent event6 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/Node")
                            .param("name", "key")
                            .param("descriptor", "I")
                            .param("instance", curr)
                            .build();
            JmcRuntime.updateEventAndYield(event6);
            if (key == currKey) {
                return false;
            } else {
                Node node = new Node(i, key);
                node.next = curr;
                // Write event to write the next member of the new node
                RuntimeEvent event7 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.WRITE_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param("newValue", curr)
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "next")
                                .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                                .param("instance", node)
                                .build();
                JmcRuntime.updateEventAndYield(event7);

                pred.next = node;
                // Write event to write the next member of the predecessor node
                RuntimeEvent event8 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.WRITE_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param("newValue", node)
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "next")
                                .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                                .param("instance", pred)
                                .build();
                JmcRuntime.updateEventAndYield(event8);

                return true;
            }
        } finally {
            l.unlock();
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
        JmcReentrantLock l = lock;

        // Read event to read the lock object
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList")
                        .param("name", "lock")
                        .param("descriptor", "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        try {
            l.lock();
            pred = head;

            // Read event to read the head node
            RuntimeEvent event1 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/coarse/CoarseList")
                            .param("name", "head")
                            .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                            .param("instance", this)
                            .build();
            JmcRuntime.updateEventAndYield(event1);

            curr = pred.next;

            // Read event to read the next member of the head node
            RuntimeEvent event2 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/Node")
                            .param("name", "next")
                            .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                            .param("instance", pred)
                            .build();
            JmcRuntime.updateEventAndYield(event2);

            int currKey = curr.key;
            // Read event to read the key of the current node
            RuntimeEvent event3 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/Node")
                            .param("name", "key")
                            .param("descriptor", "I")
                            .param("instance", curr)
                            .build();
            JmcRuntime.updateEventAndYield(event3);
            while (currKey < key) {
                pred = curr;

                Node n = curr.next;
                // Read event to read the next member of the current node
                RuntimeEvent event4 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "next")
                                .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event4);
                curr = n;

                currKey = curr.key;
                // Read event to read the key of the current node
                RuntimeEvent event5 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "key")
                                .param("descriptor", "I")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event5);
            }
            currKey = curr.key;
            // Read event to read the key of the current node
            RuntimeEvent event6 =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEvent.Type.READ_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param(
                                    "owner",
                                    "org/mpisws/jmc/programs/det/lists/list/node/Node")
                            .param("name", "key")
                            .param("descriptor", "I")
                            .param("instance", curr)
                            .build();
            JmcRuntime.updateEventAndYield(event6);
            if (key == currKey) {
                Node n = curr.next;

                // Read event to read the next member of the current node
                RuntimeEvent event7 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.READ_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "next")
                                .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                                .param("instance", curr)
                                .build();
                JmcRuntime.updateEventAndYield(event7);

                pred.next = n;
                // Write event to write the next member of the predecessor node
                RuntimeEvent event8 =
                        new RuntimeEvent.Builder()
                                .type(RuntimeEvent.Type.WRITE_EVENT)
                                .taskId(JmcRuntime.currentTask())
                                .param("newValue", n)
                                .param(
                                        "owner",
                                        "org/mpisws/jmc/programs/det/lists/list/node/Node")
                                .param("name", "next")
                                .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;")
                                .param("instance", pred)
                                .build();
                JmcRuntime.updateEventAndYield(event8);
                return true;
            } else {
                return false;
            }
        } finally {
            l.unlock();
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
        synchronized (lock) {
            pred = head;
            curr = pred.next;
            while (curr.key < key) {
                curr = curr.next;
            }
            return key == curr.key;
        }
    }
}
