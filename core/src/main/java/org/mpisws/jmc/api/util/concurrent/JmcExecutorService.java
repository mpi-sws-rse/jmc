package org.mpisws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.JmcRuntime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An executor service that runs tasks in new threads. It is a redefinition of {@link
 * java.util.concurrent.ExecutorService} for JMC model checking.
 *
 * <p>Currently, the executor service does not support stopping tasks.
 */
public class JmcExecutorService implements ExecutorService {

    private static final Logger LOGGER = LogManager.getLogger(JmcExecutorService.class);

    // Keeps track of how many current tasks are running.
    // Updated by the worker threads.
    private AtomicInteger counter;
    private int capacity;
    private BlockingQueue<JmcFuture> queue;
    private List<JmcExecutorWorker> workers;
    private AtomicBoolean isShutdown = new AtomicBoolean(false);

    public JmcExecutorService(int capacity) {
        this.capacity = capacity;
        this.counter = new AtomicInteger(0);
        this.queue = new LinkedBlockingQueue<>();
        this.workers = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            JmcExecutorWorker worker = new JmcExecutorWorker(i, this.queue, this.counter);
            workers.add(worker);
            worker.start();
        }
        this.isShutdown.set(false);
    }

    /** Stops the executor service. */
    @Override
    public void shutdown() {
        for (JmcExecutorWorker worker : workers) {
            worker.shutdown();
        }
        isShutdown.set(true);

        for (JmcExecutorWorker worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                LOGGER.error("Error while shutting down worker thread", e);
            }
        }
    }

    private void addWork() {
        counter.incrementAndGet();
    }

    private void removeWork() {
        counter.decrementAndGet();
    }

    /** Stops the executor service. Currently not supported. */
    @Override
    public List<Runnable> shutdownNow() {
        // Currently not supported
        for (JmcExecutorWorker worker : workers) {
            worker.shutdown();
        }
        isShutdown.set(true);
        return new ArrayList<>();
    }

    /** Returns whether the executor service is shutdown. */
    @Override
    public boolean isShutdown() {
        return isShutdown.get();
    }

    /** Returns whether the executor service is terminated. */
    @Override
    public boolean isTerminated() {
        return false;
    }

    /** Waits for the executor service to terminate. */
    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        boolean allShutdown = true;
        for (JmcExecutorWorker worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                allShutdown = false;
            }
        }
        return allShutdown;
    }

    private void offer(JmcFuture future) {
        if (counter.get() < capacity) {
            // If we know that the task will be immediately picked up,
            // We pause and wait for the matching yield
            Long taskId = JmcRuntime.currentTask();
            JmcRuntime.pause(taskId);
            queue.offer(future);
            JmcRuntime.wait(taskId);
        } else {
            // Otherwise, all other actual JVM threads are blocked.
            // Hence, we just yield and allow one of them to continue
            queue.offer(future);
            JmcRuntime.yield();
        }
    }

    /** Submits a callable task to the executor service. */
    @Override
    public <T> JmcFuture<T> submit(Callable<T> callable) {
        JmcFuture<T> future = new JmcFuture<>(callable, JmcRuntime.addNewTask());
        offer(future);
        return future;
    }

    @Override
    public <T> JmcFuture<T> submit(Runnable runnable, T t) {
        JmcFuture<T> future = null;
        if (runnable instanceof JmcThread thread) {
            future = new JmcFuture<>(thread, t);
        } else {
            // Otherwise, create a new JmcThread
            future = new JmcFuture<>(runnable, t, JmcRuntime.addNewTask());
        }
        offer(future);
        return future;
    }

    @Override
    public JmcFuture<?> submit(Runnable runnable) {
        JmcFuture<?> future = null;
        if (runnable instanceof JmcThread jmcThread) {
            // If the runnable is already a JmcThread, reuse the taskId
            future = new JmcFuture<>(jmcThread);
        } else {
            // Otherwise, create a new JmcThread
            future = new JmcFuture<>(runnable, JmcRuntime.addNewTask());
        }
        return future;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection)
            throws InterruptedException {
        // Map each callable to a future and run them
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> callable : collection) {
            JmcFuture<T> future = new JmcFuture<>(callable, JmcRuntime.addNewTask());
            futures.add(future);
            offer(future);
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException {
        return invokeAll(collection);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection)
            throws InterruptedException, ExecutionException {
        List<JmcFuture> futures = new ArrayList<>();
        Set<Long> allTasks = new HashSet<>();
        for (Callable<T> callable : collection) {
            JmcFuture<T> future = new JmcFuture<>(callable, JmcRuntime.addNewTask());
            futures.add(future);
            allTasks.add(future.getTaskId());
            offer(future);
        }
        while (true) {
            Set<Long> completedTasks = new HashSet<>();
            for (JmcFuture<T> future : futures) {
                if (future.isDone()) {
                    try {
                        return future.get();
                    } catch (InterruptedException e) {
                        completedTasks.add(future.getTaskId());
                    }
                }
            }
            // Check if all tasks are completed
            if (completedTasks.size() == allTasks.size()) {
                break;
            }
        }
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        return invokeAny(collection);
    }

    @Override
    public void execute(Runnable runnable) {
        if (runnable instanceof JmcThread) {
            // If the runnable is already a JmcThread, reuse the taskId
            JmcThread jmcThread = (JmcThread) runnable;
            JmcFuture jmcFuture = new JmcFuture<>(jmcThread);
            offer(jmcFuture);
        } else {
            // Otherwise, create a new JmcThread
            offer(new JmcFuture<>(runnable, JmcRuntime.addNewTask()));
        }
    }

    private static class JmcExecutorWorker extends Thread {

        private static final Logger LOGGER = LogManager.getLogger(JmcExecutorWorker.class);

        private BlockingQueue<JmcFuture> queue;
        private AtomicBoolean isShutdown = new AtomicBoolean(false);
        private AtomicInteger workCounter;
        private final int id;

        public JmcExecutorWorker(
                int id, BlockingQueue<JmcFuture> queue, AtomicInteger workCounter) {
            this.queue = queue;
            this.workCounter = workCounter;
            this.id = id;
        }

        public void shutdown() {
            isShutdown.set(true);
        }

        public boolean isShutdown() {
            return isShutdown.get();
        }

        @Override
        public void run() {
            while (!isShutdown.get()) {
                JmcFuture task = null;
                try {
                    task = queue.take();
                    LOGGER.debug("Received task {} in worker {}", task.getTaskId(), id);
                    workCounter.incrementAndGet();
                    task.run();
                    workCounter.decrementAndGet();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (task != null) {
                        if (queue.isEmpty()) {
                            JmcRuntime.join(task.getTaskId());
                        } else {
                            JmcRuntime.terminate(task.getTaskId());
                        }
                    }
                    LOGGER.debug("Completed task in worker {}", id);
                }
            }
        }
    }
}
