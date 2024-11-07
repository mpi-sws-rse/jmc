package org.mpisws.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class JMCLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

    private static final Logger LOGGER = LogManager.getLogger(JMCLinkedBlockingQueue.class);

    public int threadPoolId;

    /** */
    public JMCLinkedBlockingQueue(int threadPoolId) {
        super();
        this.threadPoolId = threadPoolId;
    }

    /**
     * @return
     * @throws InterruptedException
     */
    @Override
    public E take() throws InterruptedException {
        RuntimeEnvironment.threadAwaitForTask(Thread.currentThread());
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        // store the super.take() in a variable
        // return the variable
        E e = super.take();
        if (e instanceof Runnable r) {
            RuntimeEnvironment.taskAssignToThread(Thread.currentThread(), r);
        }
        if (Thread.currentThread() instanceof JMCStarterThread jmcStarterThread) {
            jmcStarterThread.hasTask = true;
        } else {
            LOGGER.error("The current thread is not a JMC starter thread");
            System.exit(0);
        }
        return e;
    }

    /**
     * @param e the element to add
     * @return
     */
    @Override
    public boolean offer(@NotNull E e) {
        boolean result = super.offer(e);
        if (result) {
            RuntimeEnvironment.releaseIdleThreadsInPool(threadPoolId);
        } else {
            // TODO()
        }
        return result;
    }

    /**
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return super.poll(timeout, unit);
    }
}
