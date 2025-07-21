package org.mpisws.jmc.programs.wrong.counter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.api.util.concurrent.JmcThread;
import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;

/**
 * This is simple concurrent counter program that demonstrates a deadlock between two threads over
 * using two shared locks.
 */
public class BuggyCounter extends JmcThread {

    private static final Logger LOGGER = LogManager.getLogger(BuggyCounter.class);

    Counter counter1;
    Counter counter2;
    JmcReentrantLock lock1;
    JmcReentrantLock lock2;

    public BuggyCounter(
            Counter counter1, Counter counter2, JmcReentrantLock lock1, JmcReentrantLock lock2) {
        super();
        this.counter1 = counter1;
        this.counter2 = counter2;
        this.lock1 = lock1;
        this.lock2 = lock2;
    }

    @Override
    public void run1() {
//        try {
            lock1.lock();
            this.counter1.count++;
            lock2.lock();
            this.counter2.count++;
            lock2.unlock();
            lock1.unlock();
//        } catch (JMCInterruptException e) {
//            System.out.println(
//                    "["
//                            + Thread.currentThread().getName()
//                            + " message] : The thread is interrupted");
//        }
    }

    public static void main(String[] args) {
        Counter counter1 = new Counter();
        Counter counter2 = new Counter();
        JmcReentrantLock lock1 = new JmcReentrantLock();
        JmcReentrantLock lock2 = new JmcReentrantLock();

        BuggyCounter thread1 = new BuggyCounter(counter1, counter2, lock1, lock2);
        BuggyCounter thread2 = new BuggyCounter(counter2, counter1, lock2, lock1);

        thread1.start();
        thread2.start();

        try {
            thread1.join1();
            thread2.join1();
        } catch (InterruptedException e) {
            LOGGER.error("The thread is interrupted");
        }

        System.out.println(
                "["
                        + Thread.currentThread().getName()
                        + " message] : The counter1 value is "
                        + counter1.count
                        + " and the counter2 value is "
                        + counter2.count);
    }
}
