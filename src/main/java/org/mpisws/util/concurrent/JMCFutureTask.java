package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.mpisws.runtime.JmcRuntime;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class JMCFutureTask extends FutureTask {

    public boolean isFinished = false;

    public JMCFutureTask(@NotNull Callable callable) {
        super(callable);
    }

    public JMCFutureTask(@NotNull Runnable runnable, Object result) {
        super(runnable, result);
    }

    /**
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Override
    public Object get() throws InterruptedException, ExecutionException {
        JmcRuntime.getFuture(Thread.currentThread(), this);
        return super.get();
    }

    /** */
    @Override
    protected void done() {
        this.isFinished = true;
        JmcRuntime.releaseWaitingThreadForFuture(this);
        super.done();
    }
}
