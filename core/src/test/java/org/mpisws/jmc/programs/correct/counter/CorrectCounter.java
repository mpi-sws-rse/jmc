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
        counter.count = counter.count + 1;
        lock.unlock();
    }

    public static void main(String[] args) {
        Counter counter = new Counter();
        JmcReentrantLock lock = new JmcReentrantLock();
        CorrectCounter thread1 = new CorrectCounter(counter, lock);
        CorrectCounter thread2 = new CorrectCounter(counter, lock);
        CorrectCounter thread3 = new CorrectCounter(counter, lock);
        thread1.start();
        thread2.start();
        thread3.start();
        try {
            thread1.join1();
            thread2.join1();
            thread3.join1();
            assert counter.count == 3;
            System.out.println(
                    "[Correct Counter message] : If you see this message, the assert passed. The"
                            + " counter value is "
                            + counter.count);
        } catch (InterruptedException e) {
            System.out.println("JMCInterruptException thrown");
        }
    }
}
