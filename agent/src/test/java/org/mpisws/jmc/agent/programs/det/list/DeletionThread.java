package org.mpisws.jmc.agent.programs.det.list;

import org.mpisws.jmc.api.util.concurrent.JmcThread;

public class DeletionThread extends JmcThread {

    private final Set set;
    private final int item;

    public DeletionThread(Set set, int item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run1() {
        set.remove(item);
    }
}