package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.ThreadFactory;

public class JMCSimpleThreadFactory implements ThreadFactory {

    int id;

    public JMCSimpleThreadFactory(int id) {
        super();
        this.id = id;
    }

    @Override
    public Thread newThread(Runnable r) {
        JMCStarterThread newThread = new JMCStarterThread(r, id);
        RuntimeEnvironment.addThread(newThread);
        return newThread;
    }
}
