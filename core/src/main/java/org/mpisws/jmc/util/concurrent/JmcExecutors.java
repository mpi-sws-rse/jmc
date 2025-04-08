package org.mpisws.jmc.util.concurrent;

import java.util.concurrent.Executors;

/**
 * A replacement for java.util.concurrent.Executor. Currently only supports a
 * `newSingleThreadExecutor`.
 */
public class JmcExecutors {
    public static JmcExecutorService newSingleThreadExecutor() {
        return new JmcExecutorService(1);
    }
}
