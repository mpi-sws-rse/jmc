package org.mpisws.util.concurrent;

import java.util.concurrent.ThreadFactory;

/** A thread factory that creates {@link JmcThread} instances. */
public class JmcThreadFactory implements ThreadFactory {

    private final ThreadFactory baseFactory;

    /** Create a new thread factory that wraps the given base factory. */
    public JmcThreadFactory(ThreadFactory baseFactory) {
        this.baseFactory = baseFactory;
    }

    /** Default JmcThread factory. */
    public JmcThreadFactory() {
        this.baseFactory = null;
    }

    @Override
    public Thread newThread(Runnable r) {
        if (JmcThread.class.isAssignableFrom(r.getClass())) {
            return (JmcThread) r;
        }
        if (baseFactory == null) {
            return new JmcThread(r);
        }
        return new JmcThread(baseFactory.newThread(r));
    }
}
