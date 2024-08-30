package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.ThreadFactory;

public class JMCSimpleThreadFactory implements ThreadFactory {
    public JMCSimpleThreadFactory() {
        super();
    }

    @Override
    public Thread newThread(Runnable r) {
        JMCStarterThread newThread = new JMCStarterThread(r);
        RuntimeEnvironment.addThread(newThread);
        return newThread;
    }
}
