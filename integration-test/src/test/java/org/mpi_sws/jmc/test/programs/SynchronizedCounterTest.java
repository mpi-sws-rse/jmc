package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.test.sync.SynchronizedBlockCounter;
import org.mpi_sws.jmc.test.sync.SynchronizedMethodCounter;
import org.mpi_sws.jmc.test.sync.SynchronizedCounterThread;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        assertEquals(3, counter.getCount());
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
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
        assertEquals(4, counter.getCount());
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testRandomSynchronizedBlockCounter() {
        testCounterSyncBlockProgram();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust")
    public void testTrustSynchronizedBlockCounter() {
        testCounterSyncBlockProgram();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct")
    public void testPctSynchronizedCounter() {
        twoCounterProgram();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct")
    public void testPctSynchronizedBlockCounter() {
        testCounterSyncBlockProgram();
    }
}
