package org.mpisws.util.concurrent;

import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.RuntimeEnvironment;

public class JMCStarterThread extends Thread {

    public JMCStarterThread() {
        super();
    }

    public JMCStarterThread(Runnable r) {
        super(r);
    }

    @Override
    public void run() {
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        super.run();
        try {
            RuntimeEnvironment.finishThreadRequest(Thread.currentThread());
        } catch (HaltExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {
        RuntimeEnvironment.threadStart(this, Thread.currentThread());
    }

    public void startByScheduler() {
        super.start();
    }
}
