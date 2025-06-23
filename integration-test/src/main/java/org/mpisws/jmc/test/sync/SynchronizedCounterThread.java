package org.mpisws.jmc.test.sync;

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
