package org.mpisws.concurrent.programs.correct.counter;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.JmcThread;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class CorrectCounter extends JmcThread {
    ReentrantLock lock;
    Counter counter;

    public CorrectCounter(Counter counter, ReentrantLock lock) {
        this.counter = counter;
        this.lock = lock;
    }


    @Override
    public void run1() throws JMCInterruptException {
        lock.lock();
        counter.count = counter.count + 1;
        lock.unlock();
    }

    public static void main(String[] args) {
        try {
            Counter counter = new Counter();
            ReentrantLock lock = new ReentrantLock();
            CorrectCounter thread1 = new CorrectCounter(counter, lock);
            CorrectCounter thread2 = new CorrectCounter(counter, lock);
            CorrectCounter thread3 = new CorrectCounter(counter, lock);
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join1();
            thread2.join1();
            thread3.join1();
            Utils.assertion(
                    counter.count == 3,
                    " ***The assert did not pass, the counter value is " + counter.count + "***");
            System.out.println(
                    "[Correct Counter message] : If you see this message, the assert passed. The"
                        + " counter value is "
                            + counter.count);
        } catch (InterruptedException e) {
            System.out.println("JMCInterruptException thrown");
        }
    }
}
