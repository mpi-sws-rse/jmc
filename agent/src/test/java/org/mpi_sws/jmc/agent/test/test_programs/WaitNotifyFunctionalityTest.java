package org.mpi_sws.jmc.agent.test.test_programs;

import org.junit.jupiter.api.Test;

public class WaitNotifyFunctionalityTest {
    private static class WaiterThread extends Thread {
        private final Object lock;

        private WaiterThread(Object lock) {
            this.lock = lock;
        }

        @Override
        public void run() {
            try {
                synchronized (lock) {
                    this.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public Object getLock() {
            return lock;
        }
    }

    @Test
    public void testWaitNotify() throws InterruptedException {
        WaiterThread thread = new WaiterThread(new Object());
        thread.start();

        // Give the thread some time to start and wait
        Thread.sleep(100);

        synchronized (thread.getLock()) {
            thread.getLock().notify();
        }

        thread.join();
        System.out.println("Thread has been notified and has finished execution.");
    }
}
