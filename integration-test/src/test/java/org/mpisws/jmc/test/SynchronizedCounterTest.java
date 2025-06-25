package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.sync.SynchronizedBlockCounter;
import org.mpisws.jmc.test.sync.SynchronizedMethodCounter;
import org.mpisws.jmc.test.sync.SynchronizedCounterThread;

public class SynchronizedCounterTest {

    public void twoCounterProgram() {
        SynchronizedMethodCounter counter = new SynchronizedMethodCounter();

        SynchronizedCounterThread thread1 = new SynchronizedCounterThread(counter);
        SynchronizedCounterThread thread2 = new SynchronizedCounterThread(counter);
        SynchronizedCounterThread thread3 = new SynchronizedCounterThread(counter);

        thread1.start();
        thread2.start();
        thread3.start();

        try {
            thread1.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assert counter.getCount() == 3;
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = true)
    public void testRandomSynchronizedCounter() {
        twoCounterProgram();
    }


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust")
    public void testTrustSynchronizedCounter() {
        twoCounterProgram();
    }

    public void testCounterSyncBlockProgram() {
        SynchronizedBlockCounter counter = new SynchronizedBlockCounter();

        SynchronizedCounterThread thread1 = new SynchronizedCounterThread(counter);
        SynchronizedCounterThread thread2 = new SynchronizedCounterThread(counter);
        SynchronizedCounterThread thread3 = new SynchronizedCounterThread(counter);
        SynchronizedCounterThread thread4 = new SynchronizedCounterThread(counter);

        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();

        try {
            thread1.join();
            thread2.join();
            thread3.join();
            thread4.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assert counter.getCount() == 4;
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = true)
    public void testRandomSynchronizedBlockCounter() {
        testCounterSyncBlockProgram();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust")
    public void testTrustSynchronizedBlockCounter() {
        testCounterSyncBlockProgram();
    }
}
