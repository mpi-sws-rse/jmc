package org.mpi_sws.jmc.test.sync;

public class SynchronizedCounterThread extends Thread {
    SynchronizedCounter counter;

    public SynchronizedCounterThread(SynchronizedCounter counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        counter.increment();
    }
}
