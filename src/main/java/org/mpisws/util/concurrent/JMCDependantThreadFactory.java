package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

import java.util.concurrent.ThreadFactory;

public class JMCDependantThreadFactory implements ThreadFactory {

    public ThreadFactory userDefinedThreadFactory;

    public JMCDependantThreadFactory(ThreadFactory userDefinedThreadFactory) {
        super();
        this.userDefinedThreadFactory = userDefinedThreadFactory;
    }

    @Override
    public Thread newThread(Runnable r) {
        // TODO: why do we need to do anything here. Shouldn't the bytecode instrumentation take
        //      care of instrumenting all calls to Thread with a call to JmcThread?
        Long id = JmcRuntime.addNewTask();
        JmcThread thread = new JmcThread(r, id);
        return thread;
    }
}
