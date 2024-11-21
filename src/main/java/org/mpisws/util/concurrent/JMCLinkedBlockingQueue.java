package org.mpisws.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class JMCLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

    private static final Logger LOGGER = LogManager.getLogger(JMCLinkedBlockingQueue.class);

    /**
     * TODO: complete this
     *
     * @return
     * @throws InterruptedException
     */
    @NotNull
    @Override
    public E take() throws InterruptedException {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.WAIT_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("queue", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        // store the super.take() in a variable
        // return the variable
        E e = super.take();
        if (e instanceof Runnable r) {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.TASK_ASSIGNED_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param("task", r)
                            .param("queue", this)
                            .build();
            JmcRuntime.updateEventAndYield(event);
        }
        if (Thread.currentThread() instanceof JmcThread jmcThread) {
            jmcThread.hasTask = true;
        } else {
            LOGGER.error("The current thread is not a JMC starter thread");
            System.exit(0);
        }
        return e;
    }

    /**
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        JmcRuntime.yield();
        return super.poll(timeout, unit);
    }
}
