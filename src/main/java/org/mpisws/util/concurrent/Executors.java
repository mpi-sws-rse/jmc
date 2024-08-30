package org.mpisws.util.concurrent;

import java.util.concurrent.*;

public class Executors {

    public static ExecutorService newFixedThreadPool(int nThreads) {
        JMCSimpleThreadFactory jmcSimpleThreadFactory = new JMCSimpleThreadFactory();
        return new JMCThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(), jmcSimpleThreadFactory);
    }

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory userDefinedThreadFactory) {
        JMCDependantThreadFactory jmcDependantThreadFactory = new JMCDependantThreadFactory(userDefinedThreadFactory);
        jmcDependantThreadFactory.userDefinedThreadFactory = userDefinedThreadFactory;
        return new JMCThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(), jmcDependantThreadFactory);
    }
    
}
