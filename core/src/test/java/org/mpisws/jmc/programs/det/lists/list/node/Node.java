package org.mpisws.jmc.programs.det.lists.list.node;


import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class Node {

    public int item;
    public int key;
    public Node next;

    public Node(int i) {
        item = i;
        // Write event for initializing item
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", i)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/Node")
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
                                "org/mpisws/jmc/programs/det/lists/list/node/Node")
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
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/list/node/Node")
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
                                "org/mpisws/jmc/programs/det/lists/list/node/Node")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);
    }
}
