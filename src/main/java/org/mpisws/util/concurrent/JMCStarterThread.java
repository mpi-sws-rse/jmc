package org.mpisws.util.concurrent;

import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.JmcRuntime;

public class JMCStarterThread extends Thread {

    public int threadPoolExecutorId;

    public boolean hasTask = false;

    public JMCStarterThread(int threadPoolExecutorId) {
        super();
        this.threadPoolExecutorId = threadPoolExecutorId;
    }

    public JMCStarterThread(Runnable r, int threadPoolExecutorId) {
        super(r);
        this.threadPoolExecutorId = threadPoolExecutorId;
    }

    @Override
    public void run() {
        this.hasTask = true;
        JmcRuntime.waitRequest(Thread.currentThread());
        super.run();
        try {
            JmcRuntime.finishThreadRequest(Thread.currentThread());
        } catch (HaltExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {
        JmcRuntime.threadStart(this, Thread.currentThread());
    }

    public void startByScheduler() {
        super.start();
    }
}
