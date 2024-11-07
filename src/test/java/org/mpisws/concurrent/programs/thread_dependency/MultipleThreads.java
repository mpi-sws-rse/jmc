package org.mpisws.concurrent.programs.thread_dependency;

public class MultipleThreads implements Runnable {
    Integer counter;

    public class Lock {}

    Lock lock = new Lock();

    MultipleThreads(Integer counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        synchronized (lock) {
            counter = counter + 1;
        }
        new Thread(
                        () -> {
                            synchronized (lock) {
                                counter = counter + 1;
                            }
                        })
                .run();
        synchronized (lock) {
            counter = counter + 1;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Integer counter = Integer.valueOf(0);
        MultipleThreads thread1 = new MultipleThreads(counter);
        MultipleThreads thread2 = new MultipleThreads(counter);
        var t1 = new Thread(thread1);
        var t2 = new Thread(thread2);
        t1.run();
        t2.run();
        t1.join();
        t2.join();
        assert (counter >= 4);
    }
}
