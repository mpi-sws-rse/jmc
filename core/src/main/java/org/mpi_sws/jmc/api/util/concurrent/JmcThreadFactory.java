package org.mpi_sws.jmc.api.util.concurrent;

import java.util.concurrent.ThreadFactory;

/**
 * A thread factory that creates {@link JmcThread} instances. Reimplementation of {@link
 * java.util.concurrent.ThreadFactory}
 */
public class JmcThreadFactory implements ThreadFactory {

    /** Optional delegate factory whose thread is wrapped in a {@link JmcThread}; {@code null} for the default. */
    private final ThreadFactory baseFactory;

    /** Create a new thread factory that wraps the given base factory. */
    public JmcThreadFactory(ThreadFactory baseFactory) {
        this.baseFactory = baseFactory;
    }

    /** Default JmcThread factory. */
    public JmcThreadFactory() {
        this.baseFactory = null;
    }

    /**
     * Creates a {@link JmcThread} for the given runnable: returns it directly if it is already a
     * {@code JmcThread}, otherwise wraps it (or the {@link #baseFactory}'s thread) in a new one.
     *
     * @param r the runnable to run
     * @return a {@code JmcThread} that will run {@code r}
     */
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
