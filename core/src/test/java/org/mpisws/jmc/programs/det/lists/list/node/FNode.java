package org.mpisws.jmc.programs.det.lists.list.node;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;

public class FNode {

    public int item;
    public int key;
    public FNode next;
    private final JmcReentrantLock lock;

    public FNode(int i) {
        item = i;
        // Write event for initializing item
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", i)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "item")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);

        key = i;
        // Write event for initializing key
        RuntimeEvent event2 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", i)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);

        JmcReentrantLock l = new JmcReentrantLock();
        lock = l;
        // Write event for initializing lock
        RuntimeEvent event3 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", l)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "lock")
                        .param("descriptor", "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event3);
    }

    public FNode(int item, int key) {
        this.item = item;
        // Write event for initializing item
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", item)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "item")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);

        this.key = key;
        // Write event for initializing key
        RuntimeEvent event2 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", key)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);

        JmcReentrantLock l = new JmcReentrantLock();
        lock = l;
        // Write event for initializing lock
        RuntimeEvent event3 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", l)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/FNode")
                        .param("name", "lock")
                        .param("descriptor", "Lorg/mpisws/jmc/util/concurrent/JmcReentrantLock;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event3);
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
