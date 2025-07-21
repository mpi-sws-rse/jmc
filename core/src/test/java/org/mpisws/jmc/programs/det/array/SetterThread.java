package org.mpisws.jmc.programs.det.array;

import org.mpisws.jmc.api.util.concurrent.JmcThread;

public class SetterThread extends JmcThread {

    public Array array;

    public SetterThread(Array array) {
        super();
        this.array = array;
    }

    @Override
    public void run1() {
        int t = array.x;
        array.a[t] = 1;
        array.x = t + 1;
    }
}
