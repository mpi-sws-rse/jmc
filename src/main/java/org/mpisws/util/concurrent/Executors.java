package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.*;

public class Executors {

    public static ExecutorService newFixedThreadPool(int nThreads) {
        int id = RuntimeEnvironment.nextThreadPoolExecutorId();
        JMCSimpleThreadFactory jmcSimpleThreadFactory = new JMCSimpleThreadFactory(id);
        return new JMCThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new JMCLinkedBlockingQueue<Runnable>(), jmcSimpleThreadFactory, id);
    }

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory userDefinedThreadFactory) {
        int id = RuntimeEnvironment.nextThreadPoolExecutorId();
        JMCDependantThreadFactory jmcDependantThreadFactory = new JMCDependantThreadFactory(userDefinedThreadFactory, id);
        return new JMCThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new JMCLinkedBlockingQueue<Runnable>(), jmcDependantThreadFactory, id);
    }
}