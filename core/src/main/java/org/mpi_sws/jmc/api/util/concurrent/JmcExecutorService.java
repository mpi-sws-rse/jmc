package org.mpi_sws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An executor service that runs tasks in new threads. It is a redefinition of {@link
 * java.util.concurrent.ExecutorService} for JMC model checking.
 *
 * <p>Currently, the executor service does not support stopping tasks.
 */
public class JmcExecutorService extends ThreadPoolExecutor {

    /** Logger for pool lifecycle and worker diagnostics. */
    private static final Logger LOGGER = LogManager.getLogger(JmcExecutorService.class);

    /** Number of tasks currently running; incremented/decremented by the worker threads. */
    private final AtomicInteger counter;
    /** Number of worker threads in the pool. */
    private final int capacity;
    /** Queue of submitted futures the workers pull from. */
    private final BlockingQueue<JmcFuture> queue;
    /** The pool's worker threads. */
    private final List<JmcExecutorWorker> workers;
    /** Whether the pool has been shut down. */
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Creates a pool with {@code capacity} JMC worker threads, starts them, and registers the
     * executor with the runtime so it can be shut down between iterations.
     *
     * @param capacity the number of worker threads
     */
    public JmcExecutorService(int capacity) {

        super(capacity,
                capacity,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new JmcThreadFactory());
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
        JmcRuntimeUtils.registerExecutor(this);
    }

    /**
     * Creates a pool with {@code capacity} worker threads built from the given thread factory.
     *
     * @param capacity the number of worker threads
     * @param threadFactory the factory for the underlying JVM threads
     */
    public JmcExecutorService(int capacity, ThreadFactory threadFactory) {

        super(capacity,
                capacity,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory);
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
        JmcRuntimeUtils.registerExecutor(this);
    }

    /**
     * Full {@code ThreadPoolExecutor}-shaped constructor, provided so a class that extends {@code
     * ThreadPoolExecutor} can be redirected to this executor. The pool uses {@code maximumPoolSize}
     * JMC worker threads regardless of the passed queue.
     *
     * @param corePoolSize the core pool size (passed to the superclass)
     * @param maximumPoolSize the maximum pool size; also the number of JMC workers
     * @param keepAliveTime the keep-alive time (passed to the superclass)
     * @param timeUnit the keep-alive time unit
     * @param receivedQueue the work queue (passed to the superclass)
     */
    public JmcExecutorService(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            BlockingQueue<Runnable> receivedQueue
    ) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                timeUnit,
                receivedQueue
        );
        this.capacity = maximumPoolSize;
        this.counter = new AtomicInteger(0);
        this.queue = new LinkedBlockingQueue<>();
        this.workers = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            JmcExecutorWorker worker = new JmcExecutorWorker(i, this.queue, this.counter);
            workers.add(worker);
            worker.start();
        }
        this.isShutdown.set(false);
        JmcRuntimeUtils.registerExecutor(this);
    }

    /**
     * Shuts the executor down: signals and interrupts every worker thread, marks the pool shut down,
     * and joins the workers.
     */
    @Override
    public void shutdown() {
        super.shutdown();
        for (JmcExecutorWorker worker : workers) {
            worker.shutdown();
            worker.interrupt();
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

    /** Increments the running-task counter. */
    private void addWork() {
        counter.incrementAndGet();
    }

    /** Decrements the running-task counter. */
    private void removeWork() {
        counter.decrementAndGet();
    }

    /**
     * Signals every worker to stop and marks the pool shut down. Pending tasks are not returned or
     * cancelled (best-effort shutdown is not fully supported).
     *
     * @return an empty list (pending tasks are not returned)
     */
    @Override
    public List<Runnable> shutdownNow() {
        // Currently not supported
        for (JmcExecutorWorker worker : workers) {
            worker.shutdown();
        }
        isShutdown.set(true);
        return new ArrayList<>();
    }

    /**
     * @return whether the executor has been shut down
     */
    @Override
    public boolean isShutdown() {
        return isShutdown.get();
    }

    /**
     * Reports termination; this implementation always returns {@code false} (termination is not
     * tracked).
     *
     * @return {@code false}
     */
    @Override
    public boolean isTerminated() {
        return false;
    }

    /**
     * Waits for the executor to terminate by joining all worker threads. The timeout is accepted for
     * API compatibility but is not enforced.
     *
     * @param l the timeout magnitude (ignored)
     * @param timeUnit the timeout unit (ignored)
     * @return {@code true} if all workers finished without interruption, {@code false} otherwise
     * @throws InterruptedException if interrupted
     */
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

    /**
     * Enqueues a future for the workers and hands control to the runtime.
     *
     * <p>When a worker is free (running task count below capacity) the submitting task pauses and
     * waits, so the task is picked up deterministically; otherwise it just yields to let one of the
     * blocked workers proceed.
     *
     * @param future the future (worker task) to enqueue
     */
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

    /**
     * Submits a callable, wrapping it in a {@link JmcFuture} bound to a new task and enqueuing it via
     * {@link #offer}.
     *
     * @param callable the callable to run
     * @return a {@link JmcFuture} for the task
     */
    @Override
    public <T> JmcFuture<T> submit(Callable<T> callable) {
        JmcFuture<T> future = new JmcFuture<>(callable, JmcRuntime.addNewTask());
        offer(future);
        return future;
    }

    /**
     * Submits a runnable with a fixed result. If the runnable is already a {@link JmcThread} its task
     * id is reused; otherwise a new task is allocated. The task is enqueued via {@link #offer}.
     *
     * @param runnable the runnable to run
     * @param t the result to return on completion
     * @return a {@link JmcFuture} for the task
     */
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

    /**
     * Submits a runnable (with a {@code null} result). If the runnable is already a {@link JmcThread}
     * its task id is reused; otherwise a new task is allocated. The task is enqueued via {@link
     * #offer}.
     *
     * @param runnable the runnable to run
     * @return a {@link JmcFuture} for the task
     */
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
        offer(future);
        return future;
    }

    /**
     * Submits every callable in the collection (each as a new task) and returns their futures.
     *
     * @param collection the callables to run
     * @return a list of futures, one per callable
     * @throws InterruptedException if interrupted while enqueuing
     */
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

    /**
     * Same as {@link #invokeAll(Collection)}; the timeout is accepted for API compatibility but is
     * not enforced (JMC does not model timeouts).
     *
     * @param collection the callables to run
     * @param l the timeout magnitude (ignored)
     * @param timeUnit the timeout unit (ignored)
     * @return a list of futures, one per callable
     * @throws InterruptedException if interrupted while enqueuing
     */
    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException {
        return invokeAll(collection);
    }

    /**
     * Submits every callable and returns the result of the first one that completes.
     *
     * <p>Enqueues each callable as a task, then polls the futures for one that is done and returns its
     * result (a task whose {@code get} is interrupted is counted as completed; {@code null} is
     * returned if all complete without yielding a usable result).
     *
     * @param collection the callables to run
     * @return the result of the first completed callable, or {@code null}
     * @throws InterruptedException if interrupted while enqueuing
     * @throws ExecutionException if a task completes exceptionally
     */
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

    /**
     * Same as {@link #invokeAny(Collection)}; the timeout is accepted for API compatibility but is
     * not enforced (JMC does not model timeouts).
     *
     * @param collection the callables to run
     * @param l the timeout magnitude (ignored)
     * @param timeUnit the timeout unit (ignored)
     * @return the result of the first completed callable, or {@code null}
     * @throws InterruptedException if interrupted while enqueuing
     * @throws ExecutionException if a task completes exceptionally
     * @throws TimeoutException declared for API compatibility
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        return invokeAny(collection);
    }

    /**
     * Executes a runnable on the pool without returning a future. If the runnable is already a {@link
     * JmcThread} its task id is reused; otherwise a new task is allocated. The task is enqueued via
     * {@link #offer}.
     *
     * @param runnable the runnable to run
     */
    @Override
    public void execute(Runnable runnable) {
        if (runnable instanceof JmcThread jmcThread) {
            // If the runnable is already a JmcThread, reuse the taskId
            JmcFuture jmcFuture = new JmcFuture<>(jmcThread);
            offer(jmcFuture);
        } else {
            // Otherwise, create a new JmcThread
            offer(new JmcFuture<>(runnable, JmcRuntime.addNewTask()));
        }
    }

    /**
     * A pool worker thread: repeatedly takes a {@link JmcFuture} from the shared queue and runs it,
     * joining the task when the queue drains (or terminating it otherwise), until shut down.
     */
    private static class JmcExecutorWorker extends Thread {

        /** Logger for this worker. */
        private static final Logger LOGGER = LogManager.getLogger(JmcExecutorWorker.class);

        /** The shared queue of futures to run. */
        private final BlockingQueue<JmcFuture> queue;
        /** Whether this worker has been asked to stop. */
        private final AtomicBoolean isShutdown = new AtomicBoolean(false);
        /** Shared count of running tasks, updated around each task run. */
        private final AtomicInteger workCounter;
        /** This worker's index in the pool. */
        private final int id;

        /**
         * @param id this worker's index
         * @param queue the shared queue of futures to run
         * @param workCounter the shared running-task counter
         */
        public JmcExecutorWorker(
                int id, BlockingQueue<JmcFuture> queue, AtomicInteger workCounter) {
            this.queue = queue;
            this.workCounter = workCounter;
            this.id = id;
        }

        /** Requests this worker to stop after its current task. */
        public void shutdown() {
            isShutdown.set(true);
        }

        /** @return whether this worker has been asked to stop */
        public boolean isShutdown() {
            return isShutdown.get();
        }

        /**
         * Worker loop: take a future, run it, and on completion join its task (if the queue is now
         * empty) or terminate it — until shutdown.
         */
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
                    LOGGER.debug("Interrupted", e);
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
