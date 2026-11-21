package org.mpi_sws.jmc.test.symb.lockrev;

import java.util.concurrent.locks.ReentrantLock;

/** Takes the lock and releases it. */
public class LockOnlyThread extends Thread {

    private final ReentrantLock lock;

    public LockOnlyThread(ReentrantLock lock) {
        this.lock = lock;
    }

    @Override
    public void run() {
        lock.lock();
        lock.unlock();
    }
}
