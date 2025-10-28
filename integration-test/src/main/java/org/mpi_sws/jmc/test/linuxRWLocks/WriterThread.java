package org.mpi_sws.jmc.test.linuxRWLocks;

public class WriterThread extends Thread {

    SharedData sharedData;
    RWLock mylock;

    public WriterThread(RWLock lock, SharedData data) {
        this.mylock = lock;
        this.sharedData = data;
    }

    @Override
    public void run() {
        mylock.writeLock();
        sharedData.sharedValue = 42;
        mylock.writeUnlock();
    }
}
