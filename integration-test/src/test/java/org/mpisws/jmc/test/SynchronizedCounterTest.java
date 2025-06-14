package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.sync.SynchronizedCounter;
import org.mpisws.jmc.test.sync.SynchronizedCounterThread;

public class SynchronizedCounterTest {

    public void twoCounterProgram() {
        SynchronizedCounter counter = new SynchronizedCounter();

        SynchronizedCounterThread thread1 = new SynchronizedCounterThread(counter);
        SynchronizedCounterThread thread2 = new SynchronizedCounterThread(counter);

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assert counter.getCount() == 2;
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug=true)
    public void testRandomSynchronizedCounter() {
        twoCounterProgram();
    }


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust")
    public void testTrustSynchronizedCounter() {
        twoCounterProgram();
    }
}
