package org.mpisws.util.concurrent;

import org.mpisws.runtime.HaltTaskException;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

public class JmcThread extends Thread {

    public boolean hasTask = false;
    private Long jmcThreadId;

    public JmcThread(Long jmcThreadId) {
        super();
        this.jmcThreadId = jmcThreadId;
    }

    public JmcThread(Runnable r, Long jmcThreadId) {
        super(r);
        this.jmcThreadId = jmcThreadId;
    }

    @Override
    public void run() {
        this.hasTask = true;
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.START_EVENT)
                        .taskId(jmcThreadId)
                        .build();
        JmcRuntime.updateEvent(event);
        JmcRuntime.spawn(jmcThreadId);
        try {
            super.run();
        } catch (HaltTaskException e) {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.HALT_EVENT)
                            .taskId(jmcThreadId)
                            .build();
            JmcRuntime.updateEvent(event);
        } finally {
            JmcRuntime.join(jmcThreadId);
        }
    }
}
