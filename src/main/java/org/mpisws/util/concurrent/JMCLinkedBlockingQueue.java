package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class JMCLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

    /**
     * @return
     * @throws InterruptedException
     */
    @Override
    public E take() throws InterruptedException {
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return super.take();
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
