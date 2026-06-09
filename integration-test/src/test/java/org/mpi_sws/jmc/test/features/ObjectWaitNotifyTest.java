package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectWaitNotifyTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testBasicWaitNotify() throws InterruptedException {
        final Object obj = new Object();
        final boolean[] flag = {false};

        Thread waiter = new Thread(() -> {
            synchronized (obj) {
                while (!flag[0]) {
                    try {
                        obj.wait();
                    } catch (InterruptedException e) {}
                }
            }
        });

        Thread notifier = new Thread(() -> {
            synchronized (obj) {
                flag[0] = true;
                obj.notify();
            }
        });

        waiter.start();
        Thread.sleep(50); // Ensure waiter calls wait()
        notifier.start();

        waiter.join();
        notifier.join();

        assertTrue(flag[0]);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct")
    public void testBasicWaitNotifyPct() throws InterruptedException {
        final Object obj = new Object();
        final boolean[] flag = {false};

        Thread waiter = new Thread(() -> {
            synchronized (obj) {
                while (!flag[0]) {
                    try {
                        obj.wait();
                    } catch (InterruptedException e) {}
                }
            }
        });

        Thread notifier = new Thread(() -> {
            synchronized (obj) {
                flag[0] = true;
                obj.notify();
            }
        });

        waiter.start();
        Thread.sleep(50); // Ensure waiter calls wait()
        notifier.start();

        waiter.join();
        notifier.join();

        assertTrue(flag[0]);
    }
}
