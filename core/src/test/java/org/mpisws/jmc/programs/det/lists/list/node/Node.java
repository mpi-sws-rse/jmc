package org.mpisws.jmc.programs.det.lists.list.node;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class Node {

    private int item;
    private int key;
    private Node next;

    public Node(int i) {
        item = i;
        // Write event for initializing item
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", i)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/Node")
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
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/Node")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);
    }

    public Node(int item, int key) {
        this.item = item;
        // Write event for initializing item
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", item)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/Node")
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
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/Node")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);
    }

    public int getItem() {
        int out = item;
        JmcRuntimeUtils.readEvent(
                "org/mpisws/jmc/programs/det/lists/list/node/Node", "item", "I", this);
        return out;
    }

    public int getKey() {
        int out = key;
        JmcRuntimeUtils.readEvent(
                "org/mpisws/jmc/programs/det/lists/list/node/Node", "key", "I", this);
        return out;
    }

    public Node getNext() {
        Node out = next;
        JmcRuntimeUtils.readEvent(
                "org/mpisws/jmc/programs/det/lists/list/node/Node",
                "next",
                "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;",
                this);
        return out;
    }

    public void setNext(Node next) {
        this.next = next;
        // Write event for setting next
        JmcRuntimeUtils.writeEvent(
                next,
                "org/mpisws/jmc/programs/det/lists/list/node/Node",
                "next",
                "Lorg/mpisws/jmc/programs/det/lists/list/node/Node;",
                this);
    }

    public void setItem(int item) {
        this.item = item;
        // Write event for setting item
        JmcRuntimeUtils.writeEvent(
                item, "org/mpisws/jmc/programs/det/lists/list/node/Node", "item", "I", this);
    }

    public void setKey(int key) {
        this.key = key;
        // Write event for setting key
        JmcRuntimeUtils.writeEvent(
                key, "org/mpisws/jmc/programs/det/lists/list/node/Node", "key", "I", this);
    }
}
