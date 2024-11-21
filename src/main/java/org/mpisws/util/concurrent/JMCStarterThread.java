package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

public class JMCStarterThread extends Thread {

    public boolean hasTask = false;

    public JMCStarterThread(Runnable r) {
        super(r);
    }

    @Override
    public void run() {
        this.hasTask = true;
        JmcRuntime.yield();
        super.run();
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.JOIN_EVENT)
                        .threadId(JmcRuntime.currentThread())
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    @Override
    public void start() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.START_EVENT)
                        .threadId(JmcRuntime.currentThread())
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public void startByScheduler() {
        super.start();
    }
}
