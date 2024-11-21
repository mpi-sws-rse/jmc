package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

import java.util.concurrent.ThreadFactory;

public class JMCSimpleThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
        Long newThreadId = JmcRuntime.addNewTask();
        JmcThread newThread = new JmcThread(r, newThreadId);
        return newThread;
    }
}
