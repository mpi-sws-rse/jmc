package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

import java.util.concurrent.ThreadFactory;

public class JMCSimpleThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
        JMCStarterThread newThread = new JMCStarterThread(r);
        JmcRuntime.addThread(newThread);
        return newThread;
    }
}
