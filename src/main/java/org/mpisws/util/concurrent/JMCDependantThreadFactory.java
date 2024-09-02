package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.ThreadFactory;

public class JMCDependantThreadFactory implements ThreadFactory {

    public ThreadFactory userDefinedThreadFactory;

    public JMCDependantThreadFactory(ThreadFactory userDefinedThreadFactory) {
        super();
        this.userDefinedThreadFactory = userDefinedThreadFactory;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread newThread = userDefinedThreadFactory.newThread(r);
        JMCDependantStarterThread jmcDependantStarterThread = new JMCDependantStarterThread(newThread);
        RuntimeEnvironment.addThread(jmcDependantStarterThread);
        return jmcDependantStarterThread;
    }
}
