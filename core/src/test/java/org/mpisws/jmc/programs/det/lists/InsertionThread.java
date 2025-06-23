package org.mpisws.jmc.programs.det.lists;

import org.mpisws.jmc.programs.det.lists.list.Set;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.api.util.concurrent.JmcThread;

public class InsertionThread extends JmcThread {

    private final Set set;
    public final int item;

    public InsertionThread(Set set, int item) {
        this.set = set;
        // Write event for initializing set
        RuntimeEvent event1 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", set)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/InsertionThread")
                        .param("name", "set")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/Set;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event1);

        this.item = item;
        // Write event for initializing item
        RuntimeEvent event2 =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", item)
                        .param(
                                "owner",
                                "org/mpisws/jmc/programs/det/lists/InsertionThread")
                        .param("name", "item")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event2);
    }

    @Override
    public void run1() {
        set.add(item);
    }
}
