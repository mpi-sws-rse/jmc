package org.mpisws.jmc.api.util.concurrent;

import java.util.concurrent.ExecutorService;

/**
 * A replacement for java.util.concurrent.Executor. Currently only supports a
 * `newSingleThreadExecutor`.
 */
public class JmcExecutors {
    public static ExecutorService newSingleThreadExecutor() {
        return new JmcExecutorService(1);
    }

    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new JmcExecutorService(nThreads);
    }
}
