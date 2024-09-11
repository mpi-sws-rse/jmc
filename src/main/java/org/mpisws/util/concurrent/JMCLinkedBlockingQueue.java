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
        // store the super.take() in a variable
        // return the variable
        E e = super.take();
        if (e instanceof Runnable r) {
            RuntimeEnvironment.taskAssignToThread(Thread.currentThread(), r);
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
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return super.poll(timeout, unit);
    }
}
