package org.mpisws.concurrent.programs.correct.counter;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class CorrectCounter extends Thread {
    Object lock;
    Counter counter;

    public CorrectCounter(Counter counter, Object lock) {
        this.counter = counter;
        this.lock = lock;
    }

    @Override
    public void run() {
        try {
            run2();
        } catch (JMCInterruptException e) {

        }
    }

    public void run2() throws JMCInterruptException {
        synchronized (lock) {
            counter.count = counter.count + 1;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            Counter counter = new Counter();
            ReentrantLock lock = new ReentrantLock();
            CorrectCounter thread1 = new CorrectCounter(counter, lock);
            CorrectCounter thread2 = new CorrectCounter(counter, lock);
            CorrectCounter thread3 = new CorrectCounter(counter, lock);
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
            Utils.assertion(counter.count == 3, " ***The assert did not pass, the counter value is " + counter.count + "***");
            System.out.println("[Correct Counter message] : If you see this message, the assert passed. The counter value is " + counter.count);
        } catch (JMCInterruptException e) {
            System.out.println("JMCInterruptException thrown");
        }
    }
}
