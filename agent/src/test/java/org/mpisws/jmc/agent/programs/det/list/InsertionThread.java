package org.mpisws.jmc.agent.programs.det.list;

import org.mpisws.jmc.api.util.concurrent.JmcThread;

public class InsertionThread extends JmcThread {

    private final Set set;
    public int item;

    public InsertionThread(Set set, int item) {
        this.set = set;
        this.item = item;
    }

    @Override
    public void run1() {
        set.add(item);
    }
}
