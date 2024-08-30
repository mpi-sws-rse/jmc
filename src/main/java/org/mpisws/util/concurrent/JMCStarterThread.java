package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class JMCStarterThread extends Thread {

    public JMCStarterThread(Runnable r) {
        super(r);
    }

    @Override
    public void start() {
        RuntimeEnvironment.threadStart(this, Thread.currentThread());
    }

    public void startByScheduler() {
        super.start();
    }
}
