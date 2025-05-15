package org.mpisws.jmc.programs.correct.counter;

import org.mpisws.jmc.runtime.RuntimeUtils;
import org.mpisws.jmc.util.concurrent.JmcThread;
import org.mpisws.jmc.util.concurrent.JmcReentrantLock;

public class CorrectCounter extends JmcThread {
    JmcReentrantLock lock;
    Counter counter;

    public CorrectCounter(Counter counter, JmcReentrantLock lock) {
        super();
        this.counter = counter;
        this.lock = lock;
    }

    @Override
    public void run1() {
        lock.lock();
        counter.set(counter.get() + 1);
        lock.unlock();
    }

    public static void main(String[] args) {
        Counter counter = new Counter();
        JmcReentrantLock lock = new JmcReentrantLock();
        CorrectCounter thread1 = new CorrectCounter(counter, lock);
        CorrectCounter thread2 = new CorrectCounter(counter, lock);
        CorrectCounter thread3 = new CorrectCounter(counter, lock);
        CorrectCounter thread4 = new CorrectCounter(counter, lock);
        CorrectCounter thread5 = new CorrectCounter(counter, lock);
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();
        try {
            thread1.join1();
            thread2.join1();
            thread3.join1();
            thread4.join1();
            thread5.join1();
            assert counter.get() == 5;
        } catch (InterruptedException e) {
            System.out.println("JMCInterruptException thrown");
        }
    }
}
