package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class JMCFutureTask<V> extends FutureTask<V> {

    public boolean isFinished = false;
    private Long taskId;

    public JMCFutureTask(@NotNull Callable callable) {
        super(callable);
        this.taskId = JmcRuntime.addNewTask();
    }

    public JMCFutureTask(@NotNull Runnable runnable, V result) {
        super(runnable, result);
        this.taskId = JmcRuntime.addNewTask();
    }

    /**
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.GET_FUTURE_EVENT)
                        .taskId(this.taskId)
                        .param("future", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return super.get();
    }

    @Override
    protected boolean runAndReset() {
        // Yielding to the runtime. Maybe there should be an event before this. TODO: review
        JmcRuntime.yield();
        return super.runAndReset();
    }

    @Override
    protected void set(V v) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.FUTURE_SET_EVENT)
                        .taskId(this.taskId)
                        .param("value", v)
                        .param("future", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        super.set(v);
    }

    @Override
    protected void setException(Throwable t) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.FUTURE_EXCEPTION_EVENT)
                        .taskId(this.taskId)
                        .param("exception", t)
                        .param("future", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        super.setException(t);
    }

    /** */
    @Override
    protected void done() {
        this.isFinished = true;
        JmcRuntime.join(this.taskId);
        super.done();
    }
}
