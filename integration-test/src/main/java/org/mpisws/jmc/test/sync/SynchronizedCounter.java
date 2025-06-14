package org.mpisws.jmc.test.sync;

import org.mpisws.jmc.runtime.JmcRuntimeUtils;

public class SynchronizedCounter {
    int count;

    public SynchronizedCounter() {
        count = 0;
    }

    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}
