package org.mpisws.jmc.programs.det.lists.list.node;

//
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;

public class LNode {

    public int item;
    public int key;
    public LNode next;
    private final JmcReentrantLock lock = new JmcReentrantLock();
    public boolean marked = false;

    public LNode(int i) {
        item = i;
        key = i;
    }

    public LNode(int item, int key) {
        this.item = item;
        this.key = key;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
        // Write event of the marked
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", marked)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/LNode")
                        .param("name", "marked")
                        .param("descriptor", "Z")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public boolean getMarked() {
        boolean out = marked;
        // Read event of the marked
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/LNode")
                        .param("name", "marked")
                        .param("descriptor", "Z")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return out;
    }

    public int getKey() {
        int out = key;
        // Read event of the key
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/LNode")
                        .param("name", "key")
                        .param("descriptor", "I")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return out;
    }

    public LNode getNext() {
        LNode out = next;
        // Read event of the next
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/LNode")
                        .param("name", "next")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/LNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return out;
    }

    public void setNext(LNode next) {
        this.next = next;
        // Write event of the next
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", next)
                        .param("owner", "org/mpisws/jmc/programs/det/lists/list/node/LNode")
                        .param("name", "next")
                        .param("descriptor", "Lorg/mpisws/jmc/programs/det/lists/list/node/LNode;")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
