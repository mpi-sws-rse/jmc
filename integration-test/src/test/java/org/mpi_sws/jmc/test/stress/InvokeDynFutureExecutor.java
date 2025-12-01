package org.mpi_sws.jmc.test.stress;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;


public class InvokeDynFutureExecutor {

    public static void future_seq() throws Exception {
        ExecutorService service = Executors.newSingleThreadExecutor();
        System.out.println("Invoked future_seq");

        Future<String> f = service.submit(() -> {
            System.out.println("Inside future submit");
            AtomicInteger newCounter = new AtomicInteger(0) {

                @Override
                public String toString() {
                    System.out.println("Inside toString override");
                    incrementAndGet();
                    return super.toString() + get();
                }
            };
            newCounter.toString();
            return "done";
        });
        f.get();
        service.shutdown();
    }


    public static void executor_seq() throws ExecutionException, InterruptedException {
        ExecutorService service = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        ) {
            private final ReentrantLock lock = new ReentrantLock();
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public String toString() {
                lock.lock();
                counter.incrementAndGet();
                lock.unlock();
                return "ExecutorState=" + counter.get();
            }
        };
        service.submit(() -> service.toString()).get();
        service.shutdown();
    }

    public static void futureNested_seq() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(2);

        Future<String> f = service.submit(() -> {
            ReentrantLock lock = new ReentrantLock() {

                @Override
                public String toString() {
                    this.lock();
                    try {
                        return "LockNested";
                    } finally {
                        this.unlock();
                    }
                }
            };
            return lock.toString();
        });
        f.get();
        service.shutdown();
    }

    public static void future_par() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(3);
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger counter = new AtomicInteger();

        Future<?> f1 = service.submit(() -> {
            lock.lock();
            try {
                counter.incrementAndGet();
            } finally {
                lock.unlock();
            }
        });
        Future<?> f2 = service.submit(counter::toString);

        Future<?> f3 = service.submit(() -> {
            synchronized (service) {
                counter.incrementAndGet();
            }
        });
        f1.get();
        f2.get();
        f3.get();

        service.shutdown();
    }

    /**
     * This test is disabled
     **/
//    public static void executor_par() throws Exception {
//        ExecutorService service = Executors.newCachedThreadPool();
//
//        Object syncObject = new Object();
//        AtomicInteger counter = new AtomicInteger();
//
//        Runnable r1 = () -> {
//            synchronized(syncObject) {
//                counter.incrementAndGet();
//            }
//        };
//
//        Runnable r2 = () -> counter.incrementAndGet();
//
//        Future<?> f1 = service.submit(r1);
//        Future<?> f2 = service.submit(r2);
//        Future<?> f3 = service.submit(syncObject::toString);
//
//        f1.get();
//        f2.get();
//        f3.get();
//
//        //service.shutdown();
//
//    }
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testFuture_seq() throws Exception {
        future_seq();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testExecutor_seq() throws Exception {
        executor_seq();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testFutureNested_seq() throws Exception {
        futureNested_seq();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testFuture_par() throws Exception {
        future_par();
    }

//    @JmcCheck
//    @JmcCheckConfiguration(numIterations = 10)
//    @Disabled
//    public void testExecutor_par() throws Exception {
//        executor_par();
//    }

}