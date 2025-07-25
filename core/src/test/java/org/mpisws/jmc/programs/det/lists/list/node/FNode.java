package org.mpisws.jmc.programs.det.lists.list.node;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;

public class FNode {

    private int item;
    private int key;
    private FNode next;
    private final JmcReentrantLock lock;

    public int getKey() {
        int out = key;
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return out;
    }

    public FNode getNext() {
        FNode out = next;
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "next")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return out;
    }

    public void setNext(FNode newNext) {
        this.next = newNext;
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", newNext)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "next")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/FNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public FNode(int i) {
        item = i;
        // Write event for initializing item
        JmcRuntimeEvent event1 =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", i)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "item")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);

        key = i;
        // Write event for initializing key
        JmcRuntimeEvent event2 =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", i)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);

        JmcReentrantLock l = new JmcReentrantLock();
        lock = l;
        // Write event for initializing lock
        JmcRuntimeEvent event3 =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", l)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "lock")
                        .param(
                                "descriptor",
                                "Lorg/mpisws/jmc/api/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event3);
    }

    public FNode(int item, int key) {
        this.item = item;
        // Write event for initializing item
        JmcRuntimeEvent event1 =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", item)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "item")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);

        this.key = key;
        // Write event for initializing key
        JmcRuntimeEvent event2 =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", key)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);

        JmcReentrantLock l = new JmcReentrantLock();
        lock = l;
        // Write event for initializing lock
        JmcRuntimeEvent event3 =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", l)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "lock")
                        .param(
                                "descriptor",
                                "Lorg/mpisws/jmc/api/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event3);
    }

    public void lock() {
        JmcReentrantLock l = lock;
        // Read event for accessing lock
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "lock")
                        .param(
                                "descriptor",
                                "Lorg/mpisws/jmc/api/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        l.lock();
    }

    public void unlock() {
        JmcReentrantLock l = lock;
        // Read event for accessing lock
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "lock")
                        .param(
                                "descriptor",
                                "Lorg/mpisws/jmc/api/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        l.unlock();
    }
}
