package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

import java.util.concurrent.*;

public class Executors {

    public static ExecutorService newFixedThreadPool(int nThreads) {
        int id = JmcRuntime.nextThreadPoolExecutorId();
        JMCSimpleThreadFactory jmcSimpleThreadFactory = new JMCSimpleThreadFactory(id);
        return new JMCThreadPoolExecutor(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new JMCLinkedBlockingQueue<Runnable>(id),
                jmcSimpleThreadFactory,
                id);
    }

    public static ExecutorService newFixedThreadPool(
            int nThreads, ThreadFactory userDefinedThreadFactory) {
        int id = JmcRuntime.nextThreadPoolExecutorId();
        JMCDependantThreadFactory jmcDependantThreadFactory =
                new JMCDependantThreadFactory(userDefinedThreadFactory, id);
        return new JMCThreadPoolExecutor(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new JMCLinkedBlockingQueue<Runnable>(id),
                jmcDependantThreadFactory,
                id);
    }
}
