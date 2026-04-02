package org.mpi_sws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scheduled executor service for JMC model checking.
 *
 * <p>This class extends {@link ScheduledThreadPoolExecutor} to maintain type compatibility
 * with code that casts to ScheduledThreadPoolExecutor. In JMC's controlled execution:
 * <ul>
 *   <li>Scheduling delays are modeled as yield points (no actual time delay)</li>
 *   <li>Periodic tasks (scheduleAtFixedRate, scheduleWithFixedDelay) execute once</li>
 *   <li>All tasks are executed by worker threads managed by JMC's runtime</li>
 * </ul>
 */
public class JmcScheduledExecutorService  extends ScheduledThreadPoolExecutor {

    private static final Logger LOGGER = LogManager.getLogger(JmcScheduledExecutorService.class);

    //Worker thread management - same pattern as JmcExecutorService
    private final AtomicInteger counter;
    private final int capacity;
    private final BlockingQueue<Runnable> queue;
    private final List<JmcScheduledExecutorWorker> workers;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Creates a scheduled executor service with the specified core pool size.
     *
     * @param corePoolSize the number of threads to keep in the pool
     */
    public JmcScheduledExecutorService(int corePoolSize) {
        super(corePoolSize);
        counter = new AtomicInteger(0);
        capacity = corePoolSize;
        queue = new LinkedBlockingQueue<>();
        workers = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            JmcScheduledExecutorWorker worker = new JmcScheduledExecutorWorker(i, this.queue, this.counter);
            workers.add(worker);
            worker.start();
        }
        this.isShutdown.set(false);
        JmcRuntimeUtils.registerExecutor(this);
    }

    /**
     * Creates a scheduled executor service with the specified core pool size and thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool
     * @param threadFactory the factory to use when creating new threads
     */
    public JmcScheduledExecutorService(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
        counter = new AtomicInteger(0);
        capacity = corePoolSize;
        queue = new LinkedBlockingQueue<>();
        workers = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            JmcScheduledExecutorWorker worker = new JmcScheduledExecutorWorker(i, this.queue, this.counter);
            workers.add(worker);
            worker.start();
        }
        this.isShutdown.set(false);
        JmcRuntimeUtils.registerExecutor(this);

    }

    /**
     * Add a future to the work queue and yield to allow workers to pick it up.
     * Uses the same pattern as JmcExecutorService.offer().
     */
    private void offer(Runnable runnable) {
        if (counter.get() < capacity) {
            //If we know that the task will be immediately picked up,
            // We pause and wait for the matching yield
            Long taskId = JmcRuntime.currentTask();
            JmcRuntime.pause(taskId);
            queue.offer(runnable);
            JmcRuntime.wait(taskId);
        } else {
            //Otherwise all other actual JVM threads are blocked.
            // Hence, we just yield and allow one of them to continue
            queue.offer(runnable);
            JmcRuntime.yield();
        }
    }

    /**
     * Schedule a Runnable task to execute after a delay.
     * In JMC, the delay is modeled as a yield point - the task executes immediately.
     */
    @Override
    public JmcScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        JmcScheduledFuture<?> future = new JmcScheduledFuture<>(command, JmcRuntime.addNewTask());
        offer(future);
        return future;
    }

    /**
     * Schedule a Callable task to execute after a delay.
     * In JMC, the delay is modeled as a yield point - the task executes immediately.
     */
    @Override
    public <V> JmcScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        JmcScheduledFuture<V> future = new JmcScheduledFuture<>(callable, JmcRuntime.addNewTask());
        offer(future);
        return future;
    }

    /**
     * Schedule a task to execute periodically at a fixed rate.
     * In JMC, periodic execution is not modeled - the task executes once.
     */
    @Override
    public JmcScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        //Execute once since we do not have periodic execution in Jmc
        JmcScheduledFuture<?> future = new JmcScheduledFuture<>(command, JmcRuntime.addNewTask());
        offer(future);
        return future;
    }

    /**
     * Schedule a task to execute periodically with a fixed delay between executions.
     * In JMC, periodic execution is not modeled - the task executes once.
     */
    @Override
    public JmcScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        //Execute once since there is no periodic execution in jmc
        JmcScheduledFuture<?> future = new JmcScheduledFuture(command, JmcRuntime.addNewTask());
        offer(future);
        return future;
    }


    /**
     * Submits a Callable task to the executor service.
     */
    @Override
    public <T> JmcFuture<T> submit(Callable<T> callable) {
        JmcFuture<T> future = new JmcFuture(callable, JmcRuntime.addNewTask());
        offer(future);
        return future;
    }


    /**
     * Submits a Runnable task with a result to the executor service.
     */
    @Override
    public <T> JmcFuture<T> submit(Runnable runnable, T result) {
        JmcFuture<T> future;
        if (runnable instanceof  JmcThread thread) {
            future = new JmcFuture<>(thread, result);
        } else {
            // Otherwise create a new JmcThread via JmcFuture's constructor
            future = new JmcFuture<>(runnable, result, JmcRuntime.addNewTask());
        }
        offer(future);
        return future;
    }


    /**
     * Submits a Runnable task to the executor service.
     */
    @Override
    public JmcFuture<?> submit(Runnable runnable) {
        JmcFuture<?> future;
        if (runnable instanceof  JmcThread thread) {
            future = new JmcFuture<>(thread, runnable);
        } else {
            future = new JmcFuture<>(runnable, JmcRuntime.addNewTask());
        }
        offer(future);
        return future;
    }

    /**
     * Executes a Runnable task.
     */
    @Override
    public void execute(Runnable runnable) {
        if (runnable instanceof JmcThread jmcThread ) {
            JmcFuture jmcFuture = new JmcFuture<>(jmcThread);
            offer(jmcFuture);
        } else {
            offer(new JmcFuture<>(runnable, JmcRuntime.addNewTask()));
        }
    }


    /**
     * Invokes all callable tasks and returns their futures.
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection) throws InterruptedException {
        //Map each callable to a future and run them
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> callable : collection) {
            JmcFuture<T> future = new JmcFuture<>(callable, JmcRuntime.addNewTask());
            futures.add(future);
            offer(future);
        }
        return futures;
    }


    /**
     * Invokes all callable tasks with a timeout and returns their futures.
     * Timeout is ignored in JMC.
     */
    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends  Callable<T>> collection, long l, TimeUnit timeUnit)
        throws InterruptedException {
        return invokeAll(collection);
    }

    /**
     * Invokes any callable task and returns the result of the first completed one.
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection) throws InterruptedException, ExecutionException {
        List<JmcFuture> futures = new ArrayList<>();
        Set<Long> allTasks = new HashSet<>();
        for (Callable<T> callable : collection) {
            JmcFuture<T> future = new JmcFuture<>(callable, JmcRuntime.addNewTask());
            futures.add(future);
            allTasks.add(JmcRuntime.addNewTask());
            offer(future);
        }
        while(true) {
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
            //Check if all tasks are completed
            if (completedTasks.size() == allTasks.size()) {
                break;
            }
        }
        return null;
    }


    /**
     * Invokes any callable task with a timeout and returns the result of the first completed one.
     * Timeout is ignored in JMC.
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        return invokeAny(collection);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        for (JmcScheduledExecutorWorker worker : workers) {
            worker.shutdown();
            worker.interrupt();
        }
        isShutdown.set(true);

        for (JmcScheduledExecutorWorker worker :  workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                LOGGER.error("Error while shutting down scheduled worker thread", e);
            }
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        for (JmcScheduledExecutorWorker worker : workers) {
            worker.shutdown();
        }
        isShutdown.set(true);
        return new ArrayList<>();
    }

    @Override
    public boolean isShutdown() {
        return isShutdown.get();
    }

    @Override
    public boolean isTerminated() {
        return isShutdown.get() && counter.get() == 0;
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        boolean allShutdown = true;
        for (JmcScheduledExecutorWorker worker :  workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                allShutdown = false;
            }
        }
        return allShutdown;

    }

    /**
     * Worker thread that executes tasks from the queue.
     * Handles both JmcFuture and JmcScheduledFuture.
     */
    public static class JmcScheduledExecutorWorker extends Thread {

        private static final Logger LOGGER = LogManager.getLogger(JmcScheduledExecutorWorker.class);

        private final BlockingQueue<Runnable> queue;
        private final AtomicBoolean isShutdown = new AtomicBoolean(false);
        private final AtomicInteger workCounter;
        private final int id;

        public JmcScheduledExecutorWorker(int id, BlockingQueue<Runnable> queue,
                                            AtomicInteger workCounter) {
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
                Runnable task = null;
                Long taskId = null;
                try {
                    task = queue.take();

                    //Extract taskId based on task type
                    if (task instanceof JmcFuture) {
                        taskId = ((JmcFuture<?>) task).getTaskId();

                    } else if (task instanceof JmcScheduledFuture) {
                        taskId = ((JmcScheduledFuture<?>) task).getTaskId();
                    }

                    LOGGER.debug("Scheduled worker {}  received task {}", id,  taskId);
                    workCounter.incrementAndGet();
                    task.run();
                    workCounter.decrementAndGet();

                } catch (InterruptedException e) {
                    //Worker interrupted
                } finally {
                    if (task != null && taskId != null) {
                        if (queue.isEmpty()) {
                            JmcRuntime.join(taskId);
                        } else {
                            JmcRuntime.terminate(taskId);
                        }
                    }
                    LOGGER.debug("Scheduled worker {}  completed task", id);
                }
            }
        }

    }



}
