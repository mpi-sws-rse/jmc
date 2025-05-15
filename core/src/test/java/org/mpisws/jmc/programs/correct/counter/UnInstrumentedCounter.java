package org.mpisws.jmc.programs.correct.counter;

import java.util.concurrent.locks.ReentrantLock;

public class UnInstrumentedCounter extends Thread {
    ReentrantLock lock;
    Counter counter;

    public UnInstrumentedCounter(Counter counter, ReentrantLock lock) {
        super();
        this.counter = counter;
        this.lock = lock;
    }

    @Override
    public void run() {
        lock.lock();
        counter.set(counter.get() + 1);
        lock.unlock();
    }

    public static void main(String[] args) {
        Counter counter = new Counter();
        ReentrantLock lock = new ReentrantLock();
        UnInstrumentedCounter thread1 = new UnInstrumentedCounter(counter, lock);
        UnInstrumentedCounter thread2 = new UnInstrumentedCounter(counter, lock);
        UnInstrumentedCounter thread3 = new UnInstrumentedCounter(counter, lock);
        thread1.start();
        thread2.start();
        thread3.start();
        try {
            thread1.join();
            thread2.join();
            thread3.join();
            assert counter.get() == 3;
            System.out.println(
                    "[Uninstrumented Counter message] : If you see this message, the assert passed. The"
                            + " counter value is "
                            + counter.get());
        } catch (InterruptedException e) {
            System.out.println("JMCInterruptException thrown");
        }
    }
}
